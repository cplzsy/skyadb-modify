package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred

/** One codec owner thread. Output is delivered even while the network reader is blocked. */
internal class MediaCodecPump(
    private val onFormat: (MediaFormat) -> Unit = {},
    private val onOutput: (MediaCodec, Int, MediaCodec.BufferInfo) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val thread =
        HandlerThread("skyadb-codec", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private var codec: MediaCodec? = null
    private val inputIndices = ArrayDeque<Int>()
    private var pending: Pending? = null
    private var failure: Throwable? = null
    @Volatile private var closed = false
    private val released = CompletableDeferred<Unit>()

    suspend fun configure(format: MediaFormat, surface: Surface? = null) = execute {
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
        val decoder = MediaCodec.createDecoderByType(mime)
        codec = decoder // Ownership starts before configure, including its failure path.
        if (
            surface != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                decoder.codecInfo
                    .getCapabilitiesForType(mime)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
        ) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        decoder.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    if (closed || failure != null) return
                    inputIndices.addLast(index)
                    feed()
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    if (closed || failure != null) return
                    try {
                        onOutput(codec, index, info)
                    } catch (error: Exception) {
                        fail(error)
                    }
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    if (!closed)
                        try {
                            onFormat(format)
                        } catch (error: Exception) {
                            fail(error)
                        }
                }

                override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) =
                    fail(error)
            },
            handler,
        )
        decoder.configure(format, surface, null, 0)
        decoder.start()
    }

    /** Await submission: at most one encoded packet is retained, with natural backpressure. */
    suspend fun queue(data: ByteArray, pts: Long, flags: Int = 0) {
        val submitted = CompletableDeferred<Unit>()
        check(!closed) { "Decoder closed" }
        check(
            handler.post {
                val error = failure
                when {
                    closed ->
                        submitted.completeExceptionally(IllegalStateException("Decoder closed"))
                    error != null -> submitted.completeExceptionally(error)
                    else -> {
                        check(pending == null)
                        pending = Pending(data, pts, flags, submitted)
                        feed()
                    }
                }
            }
        ) {
            "Decoder thread stopped"
        }
        submitted.await()
    }

    suspend fun close() {
        if (!closed) {
            closed = true
            handler.post {
                pending?.submitted?.completeExceptionally(IllegalStateException("Decoder closed"))
                pending = null
                inputIndices.clear()
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                codec = null
                released.complete(Unit)
                thread.quitSafely()
            }
        }
        released.await()
    }

    private fun feed() {
        val packet = pending ?: return
        if (inputIndices.isEmpty()) return
        try {
            val decoder = requireNotNull(codec)
            val index = inputIndices.removeFirst()
            val buffer = requireNotNull(decoder.getInputBuffer(index))
            check(buffer.capacity() >= packet.data.size) {
                "Encoded packet exceeds decoder input capacity"
            }
            buffer.clear()
            buffer.put(packet.data)
            decoder.queueInputBuffer(index, 0, packet.data.size, packet.pts, packet.flags)
            pending = null
            packet.submitted.complete(Unit)
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun fail(error: Throwable) {
        if (closed || failure != null) return
        failure = error
        pending?.submitted?.completeExceptionally(error)
        pending = null
        onError(error)
    }

    private suspend fun execute(block: () -> Unit) {
        val result = CompletableDeferred<Unit>()
        check(
            handler.post {
                try {
                    block()
                    result.complete(Unit)
                } catch (error: Exception) {
                    result.completeExceptionally(error)
                }
            }
        )
        result.await()
    }

    private data class Pending(
        val data: ByteArray,
        val pts: Long,
        val flags: Int,
        val submitted: CompletableDeferred<Unit>,
    )
}
