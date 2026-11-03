package com.sky22333.skyadb.scrcpy

import android.graphics.Bitmap
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.flyfishxu.kadb.usb.KadbUsb
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.model.AdbOperationResult
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbMirrorSmokeTest {
    @Test
    fun usbMirrorRendersAndSurvivesSurfaceReplacement() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(UsbManager::class.java)
        val device = KadbUsb.devices(manager).firstOrNull { manager.hasPermission(it) }
        assumeTrue("An authorized USB target is required", device != null)
        val adb = AppServices.kadbManager
        val mirror = AppServices.scrcpyRepository
        val error = AtomicReference<Throwable?>()
        // Launch with shell privileges to avoid OEM background launch restrictions in tests.
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "am start -n com.sky22333.skyadb/.MainActivity"
                )
            )
            .use { it.readBytes() }
        var resumed: MainActivity? = null
        withTimeout(10_000) {
            while (resumed == null) {
                instrumentation.runOnMainSync {
                    resumed =
                        ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>()
                            .firstOrNull()
                }
                if (resumed == null) delay(50)
            }
        }
        val activity = requireNotNull(resumed)
        val container = FrameLayout(activity)
        instrumentation.runOnMainSync { activity.setContentView(container) }

        suspend fun attachSurface(): Pair<SurfaceView, Surface> {
            val ready = CompletableDeferred<Pair<SurfaceView, Surface>>()
            instrumentation.runOnMainSync {
                val view = SurfaceView(activity)
                view.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            ready.complete(view to holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    }
                )
                container.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            return withTimeout(5_000) { ready.await() }
        }

        suspend fun assertHasFrame(view: SurfaceView) {
            val result = CompletableDeferred<Int>()
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(
                    view,
                    bitmap,
                    { result.complete(it) },
                    Handler(Looper.getMainLooper()),
                )
                assertEquals(PixelCopy.SUCCESS, withTimeout(5_000) { result.await() })
            } finally {
                bitmap.recycle()
            }
        }

        try {
            assertTrue(adb.connectUsb(manager, device!!) is AdbOperationResult.Success)
            val (firstView, firstSurface) = attachSurface()
            val sizeReceived = CompletableDeferred<Unit>()
            assertTrue(
                mirror.start(
                    firstSurface,
                    onVideoSize = { width, height ->
                        if (width > 0 && height > 0) sizeReceived.complete(Unit)
                    },
                    onStreamError = { error.set(it) },
                ) is AdbOperationResult.Success
            )
            withTimeout(5_000) { sizeReceived.await() }
            delay(1_000)
            assertHasFrame(firstView)
            mirror.clearSurface()
            instrumentation.runOnMainSync { container.removeAllViews() }
            val (secondView, secondSurface) = attachSurface()
            mirror.setSurface(secondSurface)
            delay(1_000)
            assertHasFrame(secondView)
            assertNull(error.get())
            mirror.stop()
            val shell = adb.shell("echo mirror_transport_alive")
            check(shell is AdbOperationResult.Success)
            assertEquals("mirror_transport_alive", shell.data.output.trim())
        } finally {
            mirror.stop()
            adb.disconnect()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
