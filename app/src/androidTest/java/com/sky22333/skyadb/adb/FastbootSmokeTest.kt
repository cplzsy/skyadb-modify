package com.sky22333.skyadb.adb

import android.hardware.usb.UsbManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.model.AdbOperationResult
import com.sky22333.skyadb.usb.AndroidUsbInterface
import com.sky22333.skyadb.usb.UsbOtgMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastbootSmokeTest {
    @Test
    fun summaryMultiPacketOutputAndFailureDoNotDesynchronizeNextQuery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(UsbManager::class.java)
        val device =
            manager.deviceList.values.firstOrNull {
                manager.hasPermission(it) && AndroidUsbInterface.modeOf(it) == UsbOtgMode.Fastboot
            }
        assumeTrue("An authorized OTG Fastboot target is required", device != null)
        AppServices.usbOtgHost.refresh()
        val repository = AppServices.adbRepository
        try {
            assertTrue(repository.connectUsbOtg(device!!.deviceName) is AdbOperationResult.Success)
            val summary = repository.fastbootSummary()
            assertTrue(summary is AdbOperationResult.Success)
            check(summary is AdbOperationResult.Success)
            assertFalse(summary.data.values["product"].isNullOrBlank())
            val all = repository.runFastbootCommand("getvar:all")
            assertTrue(all is AdbOperationResult.Success)
            check(all is AdbOperationResult.Success)
            assertTrue("Expected multiple INFO responses", all.data.lines().size > 2)
            Log.i(
                "FastbootSmoke",
                "getvar all: ${all.data.length} chars, ${all.data.lines().size} lines",
            )
            val failure = repository.runFastbootCommand("getvar:skyadb_missing_variable")
            assertTrue(failure is AdbOperationResult.Failure)
            check(failure is AdbOperationResult.Failure)
            assertTrue(failure.output.orEmpty().contains("FAIL"))
            assertEquals(AdbSessionKind.UsbFastboot, repository.sessionKind())
            val next = repository.runFastbootCommand("getvar:product")
            assertTrue(next is AdbOperationResult.Success)
            check(next is AdbOperationResult.Success)
            assertTrue(next.data.contains(summary.data.values.getValue("product")!!))
        } finally {
            repository.disconnect()
        }
    }
}
