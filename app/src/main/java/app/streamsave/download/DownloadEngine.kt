package app.streamsave.download

import android.content.Context
import android.net.Uri
import app.streamsave.data.DownloadStatus
import app.streamsave.data.HistoryEntry
import app.streamsave.data.HistoryStore
import app.streamsave.parser.VideoResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Independent of Activity and scheduler; process-safe checkpoints live in SQLite and filesDir. */
class DownloadEngine(
    private val context: Context,
    private val history: HistoryStore,
    private val resolver: VideoResolver,
    private val transfer: HttpTransfer,
    private val library: MediaLibrary,
) {
    private val masks = PrefixMaskStore(File(context.filesDir, "media-prefixes"))
    private val running = ConcurrentHashMap.newKeySet<Long>()
    fun partial(id: Long) = File(context.filesDir, "transfers/$id.part").also { it.parentFile!!.mkdirs() }
    suspend fun run(id: Long, network: android.net.Network? = null, report: (HistoryEntry) -> Unit) = withContext(Dispatchers.IO) {
        if (!running.add(id)) return@withContext
        var pending: String? = null
        var published = false
        try {
            var row = history.get(id) ?: return@withContext
            if (row.status == DownloadStatus.CANCELLED || row.status == DownloadStatus.COMPLETE) return@withContext
            // Recover from process death between publication and the SQLite completion update.
            if (library.isPublished(row.uri)) {
                history.update(id) { it.copy(status = DownloadStatus.COMPLETE, error = "") }
                partial(id).delete()
                return@withContext
            }
            if (row.uri.isNotBlank()) library.delete(row.uri)
            row = history.update(id) { it.copy(status = DownloadStatus.DOWNLOADING, error = "", uri = "") }!!
            report(row)
            // Re-resolve expired URLs when retrying. Do not append bytes from a different representation.
            if (row.video.videoUrl.isBlank() || !masks.exists(row.video.prefixId) || System.currentTimeMillis() - row.created > 15 * 60_000) {
                val video = resolver.resolve(row.video.sourceUrl)
                if (!video.downloadable) throw IOException(video.notice.ifBlank { "未获得下载地址" })
                if (video.videoUrl != row.video.videoUrl || video.prefixId != row.video.prefixId) partial(id).delete()
                row = history.update(id) { it.copy(video = video, etag = if (video.videoUrl == it.video.videoUrl) it.etag else "") }!!
            }
            val file = partial(id)
            if (!(row.total > 0 && row.bytes == row.total && file.length() == row.total)) {
                transfer.download(row.video.videoUrl, file, row.etag, network, masks.read(row.video.prefixId)) { done, total, etag ->
                    history.update(id) {
                        if (it.status == DownloadStatus.CANCELLED) throw CancellationException("用户取消")
                        it.copy(bytes = done, total = total, etag = etag)
                    }?.let(report)
                }
            }
            currentCoroutineContext().ensureActive()
            val meta = library.inspect(file)
            if (file.usableSpace < file.length() + 8 * 1024 * 1024) throw IOException("保存到相册需要额外空间，请清理后重试")
            history.update(id) { it.copy(status = DownloadStatus.SAVING) }?.let(report)
            val uri = library.create(row.video.title, id, meta)
            pending = uri.toString()
            history.update(id) { it.copy(uri = uri.toString()) }
            library.copy(file, uri)
            currentCoroutineContext().ensureActive()
            // This tiny commit phase is indivisible with respect to coroutine cancellation.
            withContext(NonCancellable) {
                if (history.get(id)?.status == DownloadStatus.CANCELLED) throw CancellationException("用户取消")
                library.publish(uri, file.length())
                published = true
                history.update(id) { it.copy(status = DownloadStatus.COMPLETE, bytes = file.length(), total = file.length(), error = "") }?.let(report)
                file.delete()
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                history.update(id) { if (it.status in setOf(DownloadStatus.CANCELLED, DownloadStatus.COMPLETE)) it else it.copy(status = DownloadStatus.PAUSED, error = "下载已暂停，可以继续") }
            }
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                withContext(NonCancellable) {
                    history.update(id) { if (it.status in setOf(DownloadStatus.CANCELLED, DownloadStatus.COMPLETE)) it else it.copy(status = DownloadStatus.PAUSED, error = "下载已暂停，可以继续") }
                }
                throw CancellationException("下载中断", e)
            }
            // Surface the actual failure category. No catch around startup or whole UI.
            val message = when (e) {
                is java.net.UnknownHostException -> "无法连接网络，请检查网络后重试"
                is java.net.SocketTimeoutException -> "网络连接超时，可以重试继续下载"
                is SecurityException -> "系统拒绝访问文件，请重新下载或检查系统设置"
                is IOException -> e.message ?: "文件下载失败"
                else -> "保存失败（${e.javaClass.simpleName}），请重试"
            }
            android.util.Log.w("DownloadEngine", "Transfer $id failed: ${e.javaClass.simpleName}")
            history.update(id) { if (it.status == DownloadStatus.CANCELLED) it else it.copy(status = DownloadStatus.FAILED, error = message) }?.let(report)
        } finally {
            withContext(NonCancellable) {
                if (!published && pending != null) {
                    try { library.delete(pending) ; history.update(id) { it.copy(uri = "") } }
                    catch (_: Exception) { android.util.Log.w("DownloadEngine", "Pending media cleanup will be retried for $id") }
                }
                if (history.get(id)?.status == DownloadStatus.CANCELLED) partial(id).delete()
            }
            running.remove(id)
        }
    }
}
