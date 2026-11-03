package com.sky22333.skyadb.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.flyfishxu.kadb.stream.AdbStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ScrcpyVideoDecoder(
    private val stream: AdbStream,
    private val codecId: Int,
    surface: Surface,
    private val onVideoSize: (Int, Int) -> Unit,
) {
    @Volatile private var running = true
    @Volatile private var surface: Surface = surface
    @Volatile private var surfaceValid = surface.isValid
    @Volatile private var pump: MediaCodecPump? = null
    @Volatile private var decoderError: Throwable? = null
    private var width = 0
    private var height = 0
    private var config: ByteArray? = null
    @Volatile private var awaitingKeyFrame = true
    private val surfaceEpoch = AtomicLong()
    private var decoderEpoch = -1L

    suspend fun start() =
        withContext(Dispatchers.IO) {
            val header = ByteArray(ScrcpyProtocol.PacketHeaderLength)
            val fields = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            try {
                while (running) {
                    currentCoroutineContext().ensureActive()
                    stream.source.readFully(header)
                    fields.rewind()
                    val ptsAndFlags = fields.long
                    val size = fields.int
                    if ((ptsAndFlags and ScrcpyProtocol.PacketFlagSession) != 0L) {
                        fields.position(4)
                        val nextWidth = fields.int
                        val nextHeight = fields.int
                        require(nextWidth in 1..32768 && nextHeight in 1..32768) {
                            "Invalid video size"
                        }
                        if (nextWidth != width || nextHeight != height) {
                            release()
                            width = nextWidth
                            height = nextHeight
                            config = null
                            awaitingKeyFrame = true
                            onVideoSize(width, height)
                        }
                        continue
                    }
                    require(size in 1..10_000_000) { "Invalid video packet length: $size" }
                    val data = stream.source.readByteArray(size.toLong())
                    val isConfig = (ptsAndFlags and ScrcpyProtocol.PacketFlagConfig) != 0L
                    val isKeyFrame = (ptsAndFlags and ScrcpyProtocol.PacketFlagKeyFrame) != 0L
                    if (isConfig) config = data
                    val epoch = surfaceEpoch.get()
                    if (decoderEpoch != epoch) {
                        release()
                        awaitingKeyFrame = true
                    }
                    if (width == 0 || height == 0 || !surfaceValid || !surface.isValid) continue
                    if (!isConfig && awaitingKeyFrame && !isKeyFrame) continue
                    try {
                        val existing = pump
                        val decoder = existing ?: configure(epoch)
                        if (!isConfig) awaitingKeyFrame = false
                        // configure() already submitted cached CSD once.
                        if (!isConfig)
                            decoder.queue(data, ptsAndFlags and ScrcpyProtocol.PacketPtsMask)
                        else if (existing != null)
                            decoder.queue(data, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    } catch (error: Exception) {
                        // Some hardware decoders report an abandoned Surface asynchronously.
                        // That codec belongs to the previous Surface, not the current session.
                        if (epoch == surfaceEpoch.get() && surfaceValid) throw error
                        release()
                        awaitingKeyFrame = true
                    }
                }
            } catch (error: Exception) {
                throw decoderError ?: error
            } finally {
                withContext(NonCancellable) { release() }
                runCatching { stream.close() }
            }
        }

    fun setSurface(next: Surface) {
        awaitingKeyFrame = true
        surface = next
        surfaceValid = next.isValid
        surfaceEpoch.incrementAndGet()
    }

    fun clearSurface() {
        surfaceValid = false
        awaitingKeyFrame = true
        surfaceEpoch.incrementAndGet()
    }

    fun stop() {
        running = false
        runCatching { stream.close() } // Wake a blocked network read before cancelling the job.
    }

    private suspend fun configure(epoch: Long): MediaCodecPump {
        val mime =
            when (codecId) {
                ScrcpyProtocol.CodecH264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                ScrcpyProtocol.CodecH265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
                ScrcpyProtocol.CodecAv1 -> "video/av01"
                ScrcpyProtocol.CodecVp8 -> MediaFormat.MIMETYPE_VIDEO_VP8
                ScrcpyProtocol.CodecVp9 -> MediaFormat.MIMETYPE_VIDEO_VP9
                else -> error("Unsupported video codec: $codecId")
            }
        val decoder =
            MediaCodecPump(
                onOutput = { codec, index, info ->
                    codec.releaseOutputBuffer(
                        index,
                        surfaceValid &&
                            epoch == surfaceEpoch.get() &&
                            surface.isValid &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0,
                    )
                },
                onError = { error ->
                    if (epoch == surfaceEpoch.get() && surfaceValid) {
                        decoderError = error
                        stop()
                    }
                },
            )
        pump = decoder
        decoderEpoch = epoch
        decoder.configure(MediaFormat.createVideoFormat(mime, width, height), surface)
        config?.let { decoder.queue(it, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG) }
        return decoder
    }

    private suspend fun release() {
        val decoder = pump
        pump = null
        decoder?.close()
    }
}
