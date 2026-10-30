package app.streamsave

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/** Generates an actual one-second H.264 MP4, solely for instrumented pipeline tests. */
object FixtureVideo {
    fun create(file: File): File {
        val width = 320; val height = 240
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 24)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType("video/avc")
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            var frame = 0; var track = -1; var eos = false
            val info = MediaCodec.BufferInfo()
            val deadline = System.nanoTime() + 20_000_000_000
            while (!eos) {
                check(System.nanoTime() < deadline) { "Encoder timed out" }
                if (frame <= 24) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        if (frame == 24) {
                            codec.queueInputBuffer(index, 0, 0, frame * 1_000_000L / 24, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val buffer = codec.getInputBuffer(index)!!
                            buffer.clear()
                            buffer.put(ByteArray(width * height) { (50 + frame * 5).toByte() })
                            buffer.put(ByteArray(width * height / 2) { 128.toByte() })
                            codec.queueInputBuffer(index, 0, width * height * 3 / 2, frame * 1_000_000L / 24, 0)
                        }
                        frame++
                    }
                }
                val output = codec.dequeueOutputBuffer(info, 10_000)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                } else if (output >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(output)!!
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(output, false)
                }
            }
        } finally {
            codec.stop(); codec.release()
            if (started) muxer.stop()
            muxer.release()
        }
        return file
    }
}
