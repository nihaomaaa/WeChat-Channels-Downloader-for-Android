package app.streamsave.download

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import app.streamsave.data.DownloadStatus
import app.streamsave.data.HistoryEntry
import app.streamsave.data.HistoryStore
import app.streamsave.parser.VideoInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransferScheduler(private val context: Context, private val history: HistoryStore, private val notifications: DownloadNotifications) {
    private val lock = Mutex()
    suspend fun enqueue(video: VideoInfo): HistoryEntry = lock.withLock {
        require(video.downloadable) { "此视频还没有可用的下载地址" }
        val row = history.enqueue(video)
        schedule(row)
        row
    }
    suspend fun resume(id: Long) = lock.withLock {
        val old = history.get(id) ?: return@withLock
        if (old.active || old.status == DownloadStatus.COMPLETE) return@withLock
        val row = history.update(id) { it.copy(status = DownloadStatus.QUEUED, error = "") } ?: return@withLock
        schedule(row)
    }
    private suspend fun schedule(row: HistoryEntry) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val scheduler = context.getSystemService(JobScheduler::class.java)
                if (scheduler.getPendingJob(row.jobId) != null) return
                val job = JobInfo.Builder(row.jobId, ComponentName(context, TransferJobService::class.java))
                    .setUserInitiated(true)
                    // NETWORK_TYPE_ANY implicitly requires VALIDATED in AOSP, which can stall on
                    // networks where Google's connectivity probe is unavailable. Require Internet
                    // capability and let the actual HTTPS transfer report connectivity failures.
                    .setRequiredNetwork(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build())
                    .setExtras(PersistableBundle().apply { putLong("id", row.id) })
                    .setEstimatedNetworkBytes(if (row.total > 0) row.total else JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), 0L)
                    .build()
                check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) { "系统暂时无法启动下载，请重试" }
            } else {
                ContextCompat.startForegroundService(context, Intent(context, LegacyTransferService::class.java).putExtra("id", row.id))
            }
        } catch (e: Exception) {
            history.update(row.id) { it.copy(status = DownloadStatus.FAILED, error = "无法启动后台下载，请保持应用前台后重试") }
            throw e
        }
    }
    suspend fun cancel(id: Long) {
        val row = history.get(id) ?: return
        if (!row.active && row.status != DownloadStatus.PAUSED) return
        history.update(id) { it.copy(status = DownloadStatus.CANCELLED, error = "") }
        if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(JobScheduler::class.java).cancel(row.jobId)
            TransferJobService.cancel(id)
        }
        LegacyTransferService.cancel(id)
        notifications.remove(id)
    }
    suspend fun reconcile() {
        history.load()
        val ids = if (Build.VERSION.SDK_INT >= 34) context.getSystemService(JobScheduler::class.java).allPendingJobs.map { it.id }.toSet() else emptySet()
        history.entries.value.filter { it.active && it.jobId !in ids && !LegacyTransferService.isRunning(it.id) }.forEach { row ->
            history.update(row.id) { it.copy(status = DownloadStatus.PAUSED, error = "上次下载已中断，点击继续") }
        }
    }
}
