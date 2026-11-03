package com.sky22333.skyadb.adb

import android.content.ClipboardManager
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.shell.ShellScreen
import com.sky22333.skyadb.ui.shell.ShellViewModel
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.usb.AndroidUsbInterface
import com.sky22333.skyadb.usb.UsbOtgMode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastbootUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun summaryAndQueryResultsCanBeCopiedAndOptionalRebootClearsSession() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(UsbManager::class.java)
        // Optional setup for a full Android -> Fastboot -> Android hardware regression.
        if (InstrumentationRegistry.getArguments().getString("fastbootPrepare") == "true") {
            val adbDevice =
                manager.deviceList.values.firstOrNull {
                    manager.hasPermission(it) && AndroidUsbInterface.modeOf(it) == UsbOtgMode.Adb
                }
            requireNotNull(adbDevice) { "An authorized ADB target is required for preparation" }
            AppServices.usbOtgHost.refresh()
            assertTrue(
                AppServices.adbRepository.connectUsbOtg(adbDevice.deviceName)
                    is AdbOperationResult.Success
            )
            try {
                AppServices.adbRepository.runShell("reboot bootloader")
            } finally {
                AppServices.adbRepository.disconnect()
            }
            withTimeout(30_000) {
                while (
                    manager.deviceList.values.none {
                        AndroidUsbInterface.modeOf(it) == UsbOtgMode.Fastboot
                    }
                ) delay(200)
            }
        }
        val device =
            manager.deviceList.values.firstOrNull {
                manager.hasPermission(it) && AndroidUsbInterface.modeOf(it) == UsbOtgMode.Fastboot
            }
        assumeTrue("An authorized OTG Fastboot target is required", device != null)
        AppServices.usbOtgHost.refresh()
        val repository = AppServices.adbRepository
        assertTrue(repository.connectUsbOtg(device!!.deviceName) is AdbOperationResult.Success)
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "am start -n com.sky22333.skyadb/.MainActivity"
                )
            )
            .use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync {
                    activity =
                        ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>()
                            .firstOrNull()
                }
                if (activity == null) delay(50)
            }
        }
        val resumed = requireNotNull(activity)
        lateinit var viewModel: ShellViewModel
        instrumentation.runOnMainSync {
            viewModel = ViewModelProvider(resumed)[ShellViewModel::class.java]
            resumed.setContent {
                AdbManagerTheme { ShellScreen(onBackClick = {}, viewModel = viewModel) }
            }
        }
        fun clipboardText(): String {
            var text = ""
            instrumentation.runOnMainSync {
                text =
                    resumed
                        .getSystemService(ClipboardManager::class.java)
                        .primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(resumed)
                        ?.toString()
                        .orEmpty()
            }
            return text
        }
        try {
            compose.waitUntil(10_000) {
                viewModel.uiState.value.summary != null && !viewModel.uiState.value.summaryLoading
            }
            val product = viewModel.uiState.value.summary!!.values.getValue("product")!!
            compose.onNodeWithText(product).assertIsDisplayed()
            // Re-enumeration must deliver to this Activity without replacing its navigation/UI.
            ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "am start -n com.sky22333.skyadb/.MainActivity -a android.hardware.usb.action.USB_DEVICE_ATTACHED"
                    )
                )
                .use { it.readBytes() }
            compose.waitForIdle()
            instrumentation.runOnMainSync {
                val current =
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>()
                        .single()
                assertSame(resumed, current)
            }
            compose.onNodeWithText(product).assertIsDisplayed()
            listOf(
                    R.string.fastboot_reboot_system,
                    R.string.fastboot_reboot_bootloader,
                    R.string.fastboot_reboot_recovery,
                    R.string.fastboot_reboot_fastbootd,
                )
                .forEach {
                    compose.onNode(hasText(appString(it)) and hasClickAction()).assertIsEnabled()
                }
            compose.waitForIdle()
            delay(150)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "fastboot-ui-test.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            compose
                .onNodeWithContentDescription(appString(R.string.fastboot_copy_summary))
                .performClick()
            compose.waitUntil(5000) { clipboardText().contains(product) }
            compose
                .onNode(hasSetTextAction())
                .performScrollTo()
                .performTextInput("fastboot getvar all")
            compose
                .onNodeWithText(appString(R.string.action_execute))
                .performScrollTo()
                .performClick()
            compose.waitUntil(10_000) {
                viewModel.uiState.value.operationStatus is OperationStatus.Success
            }
            val output = viewModel.uiState.value.output
            assertTrue(output.lines().size > 2)
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(2)
            compose
                .onNodeWithContentDescription(appString(R.string.command_copy_result))
                .performClick()
            compose.waitUntil(5000) { clipboardText() == output }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(0)
            // Reboot validation is explicitly opt-in, so normal instrumentation runs are read-only.
            val reboot = InstrumentationRegistry.getArguments().getString("fastbootReboot")
            if (reboot != null) {
                val label =
                    when (reboot) {
                        "bootloader" -> R.string.fastboot_reboot_bootloader
                        "system" -> R.string.fastboot_reboot_system
                        else -> error("Unsupported test reboot target")
                    }
                compose.onNode(hasText(appString(label)) and hasClickAction()).performClick()
                compose.waitUntil(10_000) {
                    viewModel.uiState.value.operationStatus is OperationStatus.Success &&
                        !viewModel.uiState.value.connected
                }
                compose
                    .onNodeWithText(appString(R.string.fastboot_reboot_system))
                    .assertIsNotEnabled()
                assertEquals(AdbSessionKind.None, repository.sessionKind())
            }
        } finally {
            repository.disconnect()
            instrumentation.runOnMainSync { resumed.finish() }
        }
    }
}
