package app.streamsave.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

data class VideoMetadata(val duration: Long, val width: Int, val height: Int)

class MediaLibrary(private val context: Context) {
    private val resolver get() = context.contentResolver
    fun inspect(file: File): VideoMetadata {
        file.inputStream().use {
            val bytes = ByteArray(12)
            if (it.read(bytes) != 12 || String(bytes, 4, 4, Charsets.US_ASCII) != "ftyp") throw IOException("响应不是标准 MP4，已阻止保存无效文件")
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val track = extractor.getTrackFormat(i)
                if (track.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    return VideoMetadata(if (track.containsKey(MediaFormat.KEY_DURATION)) track.getLong(MediaFormat.KEY_DURATION) / 1000 else 0,
                        track.getInteger(MediaFormat.KEY_WIDTH), track.getInteger(MediaFormat.KEY_HEIGHT))
                }
            }
            throw IOException("下载文件没有可播放的视频轨道")
        } catch (e: IllegalArgumentException) {
            throw IOException("MP4 文件损坏或格式不支持", e)
        } finally { extractor.release() }
    }

    fun create(title: String, id: Long, metadata: VideoMetadata): Uri {
        val safe = title.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").take(45).trim().ifBlank { "视频号" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safe}_${id}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/微信视频号下载")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.DURATION, metadata.duration)
            put(MediaStore.Video.Media.WIDTH, metadata.width)
            put(MediaStore.Video.Media.HEIGHT, metadata.height)
        }
        return resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("系统相册无法创建视频，请检查存储空间")
    }

    suspend fun copy(file: File, uri: Uri) {
        resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.flush()
        } } ?: throw IOException("无法写入系统相册")
    }
    fun publish(uri: Uri, length: Long) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0); put(MediaStore.Video.Media.SIZE, length) }
        if (resolver.update(uri, values, null, null) != 1) throw IOException("相册入库失败")
    }
    fun isPublished(raw: String): Boolean {
        if (raw.isBlank()) return false
        return try { resolver.query(Uri.parse(raw), arrayOf(MediaStore.Video.Media.IS_PENDING), null, null, null)?.use {
            it.moveToFirst() && it.getInt(0) == 0
        } ?: false } catch (_: SecurityException) { false } catch (_: IllegalArgumentException) { false }
    }
    fun exists(raw: String): Boolean {
        if (!isPublished(raw)) return false
        return try { resolver.openAssetFileDescriptor(Uri.parse(raw), "r")?.use { true } ?: false }
        catch (_: java.io.FileNotFoundException) { false } catch (_: SecurityException) { false }
    }
    fun delete(raw: String) {
        if (raw.isBlank()) return
        val uri = Uri.parse(raw)
        // Android 16 may throw SecurityException when deleting an already removed row.
        val present = resolver.query(uri, arrayOf(MediaStore.Video.Media._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        if (present) resolver.delete(uri, null, null)
    }
}

