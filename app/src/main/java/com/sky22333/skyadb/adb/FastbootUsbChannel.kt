package com.sky22333.skyadb.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** The upstream void send/receive API discards short writes and actual packet lengths. */
internal class FastbootUsbChannel(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val isPresent: () -> Boolean,
) : FastbootChannel {
    private val closed = AtomicBoolean()
    private val input = endpoint(UsbConstants.USB_DIR_IN)
    private val output = endpoint(UsbConstants.USB_DIR_OUT)

    init {
        check(connection.claimInterface(usbInterface, true)) {
            "Cannot claim Fastboot USB interface"
        }
    }

    private fun endpoint(direction: Int): UsbEndpoint =
        (0 until usbInterface.endpointCount)
            .asSequence()
            .map(usbInterface::getEndpoint)
            .firstOrNull {
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == direction
            } ?: error("Missing Fastboot bulk endpoint")

    override fun send(packet: ByteArray, timeoutMs: Int) {
        check(!closed.get()) { "Fastboot USB closed" }
        val written = connection.bulkTransfer(output, packet, packet.size, timeoutMs)
        if (written != packet.size)
            throw IOException("Fastboot USB short write: $written/${packet.size}")
    }

    override fun receive(buffer: ByteArray, timeoutMs: Int): Int {
        check(!closed.get()) { "Fastboot USB closed" }
        val count = connection.bulkTransfer(input, buffer, buffer.size, timeoutMs)
        if (count < 0) {
            if (!isPresent()) throw IOException("Fastboot USB disconnected")
            // A failed transfer can return immediately; do not spin while awaiting the deadline.
            Thread.sleep(minOf(timeoutMs, 10).toLong())
        }
        return count.coerceAtLeast(0)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { connection.releaseInterface(usbInterface) }
            connection.close()
        }
    }
}
