package app.streamsave.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.resumeWithException

/** Bounded-memory download. Never append a 200 response or a mismatched byte range. */
class HttpTransfer(private val client: OkHttpClient) {
    suspend fun download(url: String, file: File, etag: String, network: android.net.Network? = null, prefix: ByteArray = ByteArray(0), progress: suspend (Long, Long, String) -> Unit) {
        val start = if (etag.isNotEmpty()) file.length() else 0L
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("User-Agent", app.streamsave.parser.ChannelsResolver.USER_AGENT)
        if (start > 0) request.header("Range", "bytes=$start-").header("If-Range", etag)
        val transport = if (network == null) client else client.newBuilder().socketFactory(network.socketFactory)
            .dns(object : okhttp3.Dns { override fun lookup(hostname: String) = network.getAllByName(hostname).toList() }).build()
        val call = transport.newCall(request.build())
        val cancellationWatcher = CoroutineScope(currentCoroutineContext() + Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            val response = call.await()
            response.use {
                if (it.code == 416) {
                    file.delete()
                    throw IOException("服务器拒绝续传，请重新下载")
                }
                if (!it.isSuccessful) throw IOException(when (it.code) {
                    401, 403, 404 -> "视频地址已失效，请重新解析后下载"
                    else -> "下载服务返回 HTTP ${it.code}"
                })
                val body = it.body ?: throw IOException("下载响应为空")
                val length = body.contentLength()
                val range = it.header("Content-Range")
                val offset: Long
                val total: Long
                if (it.code == 206) {
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(range.orEmpty())
                        ?: throw IOException("服务器返回了无效的续传范围")
                    offset = match.groupValues[1].toLong()
                    total = match.groupValues[3].toLong()
                    if (offset != start || match.groupValues[2].toLong() < offset || total <= offset) throw IOException("续传位置不一致，请重新下载")
                } else {
                    offset = 0
                    total = length
                }
                val validator = it.header("ETag")?.takeUnless { tag -> tag.startsWith("W/") }
                    ?: it.header("Last-Modified").orEmpty()
                val available = file.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (total > 0 && total - offset + 8 * 1024 * 1024 > available) throw IOException("手机存储空间不足")
                RandomAccessFile(file, "rw").use { out ->
                    if (offset == 0L) out.setLength(0)
                    out.seek(offset)
                    var done = offset
                    var reported = 0L
                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { stream ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            for (i in 0 until minOf(count, (prefix.size.toLong() - done).coerceAtLeast(0).toInt())) {
                                buffer[i] = (buffer[i].toInt() xor prefix[done.toInt() + i].toInt()).toByte()
                            }
                            out.write(buffer, 0, count)
                            done += count
                            if (total > 0 && done > total) throw IOException("视频长度与服务器声明不符")
                            val now = System.nanoTime()
                            if (now - reported > 500_000_000) { progress(done, total, validator); reported = now }
                        }
                    }
                    if (total >= 0 && done != total) throw IOException("视频传输中断，可点击重试继续下载")
                    if (done < 16) throw IOException("下载内容为空或不是视频")
                    out.fd.sync()
                    progress(done, done, validator)
                }
            }
        } catch (e: IOException) {
            // OkHttp unblocks a cancelled read with SocketException. Preserve coroutine cancellation semantics.
            currentCoroutineContext().ensureActive()
            throw e
        } finally { cancellationWatcher.cancel() }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
