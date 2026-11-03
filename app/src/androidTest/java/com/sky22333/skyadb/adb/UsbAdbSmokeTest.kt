package com.sky22333.skyadb.adb

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flyfishxu.kadb.usb.KadbUsb
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.model.AdbOperationResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbAdbSmokeTest {
    @Test
    fun authorizedUsbSupportsConcurrentShellAndFileRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(UsbManager::class.java)
        val device = KadbUsb.devices(manager).firstOrNull { manager.hasPermission(it) }
        assumeTrue("Connect and grant USB permission before the hardware test", device != null)
        val adb = AppServices.kadbManager
        val source = File.createTempFile("usb-smoke", ".bin", context.cacheDir)
        val destination = File.createTempFile("usb-received", ".bin", context.cacheDir)
        val remote = "/data/local/tmp/skyadb-usb-smoke-${System.nanoTime()}.bin"
        try {
            val connection = adb.connectUsb(manager, device!!)
            assertTrue(
                "USB connection failed: $connection",
                connection is AdbOperationResult.Success,
            )
            coroutineScope {
                val first = async(Dispatchers.IO) { adb.shell("echo usb_first") }
                val second = async(Dispatchers.IO) { adb.shell("echo usb_second") }
                val firstResult = first.await()
                val secondResult = second.await()
                check(firstResult is AdbOperationResult.Success)
                check(secondResult is AdbOperationResult.Success)
                assertEquals("usb_first", firstResult.data.output.trim())
                assertEquals("usb_second", secondResult.data.output.trim())
            }
            val data = ByteArray(192 * 1024) { (it * 31).toByte() }
            source.writeBytes(data)
            assertTrue(adb.push(source, remote) is AdbOperationResult.Success)
            assertTrue(adb.pull(remote, destination) is AdbOperationResult.Success)
            assertArrayEquals(data, destination.readBytes())
        } finally {
            adb.shell("rm -f '$remote'")
            adb.disconnect()
            source.delete()
            destination.delete()
        }
    }
}
