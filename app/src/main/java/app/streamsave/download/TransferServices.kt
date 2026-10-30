package app.streamsave.download

import android.app.Service
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.RequiresApi
import app.streamsave.StreamSaveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(34)
class TransferJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeParameters = mutableSetOf<JobParameters>()
    private val graph get() = (application as StreamSaveApp).graph
    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getLong("id", -1)
        if (id < 0) return false
        activeParameters.add(params)
        setNotification(params, params.jobId, graph.notifications.build(id), JOB_END_NOTIFICATION_POLICY_DETACH)
        jobs[id] = scope.launch {
            try { graph.engine.run(id, params.network, graph.notifications::update) }
            finally { jobs.remove(id); if (activeParameters.remove(params)) jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        activeParameters.remove(params)
        jobs.remove(params.extras.getLong("id"))?.cancel()
        // User stop must be respected. Network/system interruption can resume the same job.
        return params.stopReason != JobParameters.STOP_REASON_USER
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private val jobs = ConcurrentHashMap<Long, Job>()
        fun cancel(id: Long) { jobs.remove(id)?.cancel() }
    }
}

class LegacyTransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph get() = (application as StreamSaveApp).graph
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra("id", -1) ?: -1
        if (id < 0) { stopSelf(startId); return START_NOT_STICKY }
        startForeground((id + 1000).toInt(), graph.notifications.build(id), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (jobs.containsKey(id)) return START_NOT_STICKY
        jobs[id] = scope.launch {
            val wakeLock = getSystemService(android.os.PowerManager::class.java)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "StreamSave:Transfer")
            wakeLock.acquire(6 * 60 * 60 * 1000L)
            try { graph.engine.run(id, report = graph.notifications::update) }
            finally {
                if (wakeLock.isHeld) wakeLock.release()
                jobs.remove(id)
                if (jobs.isEmpty()) { stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { scope.cancel(); stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private val jobs = ConcurrentHashMap<Long, Job>()
        fun isRunning(id: Long) = jobs.containsKey(id)
        fun cancel(id: Long) { jobs.remove(id)?.cancel() }
    }
}

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val graph = (context.applicationContext as StreamSaveApp).graph
        graph.scope.launch { try { graph.scheduler.cancel(intent.getLongExtra("id", -1)) } finally { pending.finish() } }
    }
}
