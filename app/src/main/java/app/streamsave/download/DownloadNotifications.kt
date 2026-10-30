package app.streamsave.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.streamsave.MainActivity
import app.streamsave.R
import app.streamsave.data.DownloadStatus
import app.streamsave.data.HistoryEntry

class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel("downloads", "视频下载", NotificationManager.IMPORTANCE_LOW)) }
    fun build(id: Long, row: HistoryEntry? = null): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getBroadcast(context, id.toInt(), Intent(context, CancelReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val active = row?.active != false
        val text = when (row?.status) {
            DownloadStatus.COMPLETE -> "已保存到相册 · Movies/微信视频号下载"
            DownloadStatus.SAVING -> "正在保存到系统相册"
            DownloadStatus.FAILED -> row.error
            DownloadStatus.CANCELLED -> "下载已取消"
            DownloadStatus.PAUSED -> "下载已暂停"
            else -> if (row != null && row.total > 0) "已下载 ${row.bytes * 100 / row.total}%" else "正在准备下载"
        }
        return Notification.Builder(context, "downloads").setSmallIcon(R.drawable.ic_download)
            .setContentTitle(row?.video?.title?.take(60) ?: "微信视频号下载 · 视频下载")
            .setContentText(text).setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(active).setAutoCancel(!active).setCategory(Notification.CATEGORY_PROGRESS)
            .apply {
                if (active) {
                    setProgress(100, if ((row?.total ?: -1) > 0) (row!!.bytes * 100 / row.total).toInt().coerceIn(0, 100) else 0, (row?.total ?: -1) <= 0 || row?.status == DownloadStatus.SAVING)
                    addAction(Notification.Action.Builder(null, "取消", cancel).build())
                }
            }.build()
    }
    fun update(row: HistoryEntry) { if (manager.areNotificationsEnabled()) manager.notify(row.jobId, build(row.id, row)) }
    fun remove(id: Long) { manager.cancel((id + 1000).toInt()) }
}

