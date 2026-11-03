package com.sky22333.skyadb.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException

internal interface FastbootChannel : Closeable {
    fun send(packet: ByteArray, timeoutMs: Int)

    /** Zero means no packet yet. Implementations must preserve USB packet boundaries. */
    fun receive(buffer: ByteArray, timeoutMs: Int): Int
}

internal data class FastbootReply(val successful: Boolean, val value: String, val output: String)

internal class FastbootExchangeException(val output: String, cause: Exception) :
    IOException(cause.message, cause)

/** One command, one terminal response. Never retry a command while draining INFO/TEXT. */
internal class FastbootProtocol(
    private val channel: FastbootChannel,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun execute(
        command: String,
        timeoutMs: Int = 10_000,
        checkActive: () -> Unit = {},
    ): FastbootReply {
        require(
            command.isNotEmpty() && command.length <= 4096 && command.all { it.code in 32..126 }
        ) {
            "Fastboot requires a single ASCII command of at most 4096 bytes"
        }
        require(timeoutMs > 0)
        val deadline = nanoTime() + timeoutMs * 1_000_000L
        val output = ByteArrayOutputStream()
        var truncated = false
        fun append(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
            // Reserve room for the terminal status even when verbose INFO fills the buffer.
            val remaining = MaxOutputBytes - 512 - output.size()
            if (count > remaining) truncated = true
            if (remaining > 0) output.write(bytes, offset, minOf(count, remaining))
        }
        fun text() = output.toString(Charsets.UTF_8.name()) + if (truncated) "\n…" else ""
        fun remaining(): Int {
            checkActive()
            val left = deadline - nanoTime()
            if (left <= 0) throw SocketTimeoutException("Fastboot response timed out")
            return ((left + 999_999L) / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        try {
            channel.send(command.toByteArray(Charsets.US_ASCII), minOf(remaining(), 1000))
            val buffer = ByteArray(256)
            while (true) {
                val count = channel.receive(buffer, minOf(remaining(), 250))
                checkActive()
                if (count == 0) continue
                if (count !in 4..buffer.size)
                    throw IOException("Invalid Fastboot packet length: $count")
                val status = String(buffer, 0, 4, Charsets.US_ASCII)
                var end = 4
                while (end < count && buffer[end] != 0.toByte()) end++
                when (status) {
                    "INFO" -> {
                        append(InfoPrefix)
                        append(buffer, 4, end - 4)
                        append(Newline)
                    }
                    "TEXT" -> append(buffer, 4, end - 4)
                    "OKAY",
                    "FAIL" -> {
                        val prefix = text()
                        val payload = String(buffer, 4, end - 4, Charsets.UTF_8)
                        val separator =
                            if (prefix.isNotEmpty() && prefix.last() != '\n') "\n" else ""
                        val terminal = "$status${if (payload.isEmpty()) "" else ": $payload"}"
                        return FastbootReply(
                            status == "OKAY",
                            payload,
                            prefix + separator + terminal,
                        )
                    }
                    "DATA" ->
                        throw IOException("Fastboot data transfer is not supported by this console")
                    else -> throw IOException("Unknown Fastboot response: $status")
                }
            }
        } catch (error: Exception) {
            runCatching { channel.close() }
            if (error is CancellationException) throw error
            throw FastbootExchangeException(text(), error)
        }
    }

    companion object {
        const val MaxOutputBytes = 64 * 1024
        private val InfoPrefix = "(bootloader) ".toByteArray(Charsets.US_ASCII)
        private val Newline = byteArrayOf(10)
    }
}
