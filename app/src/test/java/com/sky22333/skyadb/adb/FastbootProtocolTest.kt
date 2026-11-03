package com.sky22333.skyadb.adb

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class FastbootProtocolTest {
    private class Channel(vararg packets: String) : FastbootChannel {
        val replies = ArrayDeque(packets.map { it.toByteArray() })
        val sent = mutableListOf<String>()
        var closed = false
        var readCount = 0

        override fun send(packet: ByteArray, timeoutMs: Int) {
            sent += packet.toString(Charsets.US_ASCII)
        }

        override fun receive(buffer: ByteArray, timeoutMs: Int): Int {
            readCount++
            if (replies.isEmpty()) return 0
            val packet = replies.removeFirst()
            packet.copyInto(buffer)
            return packet.size
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun textFragmentsPreserveUtf8AcrossPacketBoundaries() {
        val channel = Channel()
        val encoded = "你好".toByteArray(Charsets.UTF_8)
        channel.replies.addLast("TEXT".toByteArray() + encoded.copyOfRange(0, 2))
        channel.replies.addLast("TEXT".toByteArray() + encoded.copyOfRange(2, encoded.size))
        channel.replies.addLast("OKAY".toByteArray())
        assertEquals("你好\nOKAY", FastbootProtocol(channel).execute("getvar:all").output)
    }

    @Test
    fun drainsInfoAndTextUntilTerminalWithoutResending() {
        val channel = Channel("INFOproduct: test", "TEXTpart ", "TEXTtwo\u0000ignored", "OKAYdone")
        val reply = FastbootProtocol(channel).execute("getvar:all")
        assertTrue(reply.successful)
        assertEquals("done", reply.value)
        assertEquals("(bootloader) product: test\npart two\nOKAY: done", reply.output)
        assertEquals(listOf("getvar:all"), channel.sent)
        assertEquals(4, channel.readCount)
        assertFalse(channel.closed)
    }

    @Test
    fun terminalFailureKeepsConnectionAndNextCommandReadsItsOwnReply() {
        val channel = Channel("INFOchecking", "FAILunknown variable", "OKAYnext")
        val protocol = FastbootProtocol(channel)
        val failure = protocol.execute("getvar:missing")
        assertFalse(failure.successful)
        assertTrue(failure.output.contains("FAIL: unknown variable"))
        assertFalse(channel.closed)
        assertEquals("next", protocol.execute("getvar:product").value)
        assertTrue(channel.replies.isEmpty())
    }

    @Test
    fun handlesFull256ByteResponseAndEmptyTerminal() {
        val channel = Channel("INFO" + "x".repeat(252), "OKAY")
        val reply = FastbootProtocol(channel).execute("getvar:all")
        assertTrue(reply.successful)
        assertTrue(reply.output.contains("x".repeat(252)))
        assertEquals("", reply.value)
    }

    @Test
    fun boundsOutputButStillDrainsEveryResponse() {
        val channel = Channel(*(List(600) { "INFO" + "x".repeat(252) } + "OKAY").toTypedArray())
        val reply = FastbootProtocol(channel).execute("getvar:all")
        assertTrue(reply.successful)
        assertTrue(reply.output.length <= FastbootProtocol.MaxOutputBytes)
        assertTrue(reply.output.contains("…"))
        assertTrue(reply.output.endsWith("OKAY"))
        assertTrue(channel.replies.isEmpty())
    }

    @Test
    fun timeoutIsTotalBudgetEvenWhenInfoKeepsArriving() {
        val channel = Channel(*(List(30) { "INFOstill working" }).toTypedArray())
        var time = 0L
        val protocol = FastbootProtocol(channel) { time.also { time += 10_000_000L } }
        val error =
            assertThrows(FastbootExchangeException::class.java) {
                protocol.execute("getvar:all", 100)
            }
        assertTrue(error.cause is SocketTimeoutException)
        assertTrue(error.output.contains("still working"))
        assertTrue(channel.closed)
    }

    @Test
    fun cancellationClosesConnectionAndPropagates() {
        val channel = Channel("INFOpartial", "OKAY")
        var checks = 0
        assertThrows(CancellationException::class.java) {
            FastbootProtocol(channel).execute("getvar:all") {
                if (++checks == 4) throw CancellationException()
            }
        }
        assertTrue(channel.closed)
        assertEquals(listOf("getvar:all"), channel.sent)
    }

    @Test
    fun unexpectedDataMalformedAndUnknownPacketsCloseConnection() {
        listOf("DATA00001000", "BAD", "NOPEwhatever").forEach { packet ->
            val channel = Channel(packet)
            assertThrows(FastbootExchangeException::class.java) {
                FastbootProtocol(channel).execute("getvar:product")
            }
            assertTrue(channel.closed)
        }
    }

    @Test
    fun failedWritesCloseWithoutAttemptingToReadOrRetry() {
        val channel =
            object : FastbootChannel {
                var closed = false

                override fun send(packet: ByteArray, timeoutMs: Int) {
                    throw IOException("short write")
                }

                override fun receive(buffer: ByteArray, timeoutMs: Int): Int =
                    error("must not read")

                override fun close() {
                    closed = true
                }
            }
        assertThrows(FastbootExchangeException::class.java) {
            FastbootProtocol(channel).execute("reboot")
        }
        assertTrue(channel.closed)
    }

    @Test
    fun invalidCommandsAreRejectedBeforeWritingAndKeepConnection() {
        listOf("", "reboot\nreboot", "中文", "x".repeat(4097)).forEach { command ->
            val channel = Channel()
            assertThrows(IllegalArgumentException::class.java) {
                FastbootProtocol(channel).execute(command)
            }
            assertTrue(channel.sent.isEmpty())
            assertFalse(channel.closed)
        }
    }
}
