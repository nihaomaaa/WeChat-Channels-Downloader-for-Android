package app.streamsave

import android.app.Application
import android.content.Context
import app.streamsave.data.HistoryStore
import app.streamsave.data.Preferences
import app.streamsave.download.*
import app.streamsave.parser.ResolvedVideoCache
import app.streamsave.data.TencentSession
import app.streamsave.parser.LocalTencentResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class StreamSaveApp : Application() {
    val graph by lazy { AppGraph(this) }
}
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val preferences = Preferences(context)
    val history = HistoryStore(context)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(false).build()
    val tencentSession = TencentSession(context)
    val localResolver = LocalTencentResolver(client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build(),
        tencentSession::credentials, tencentSession::accepted, tencentSession::rejected,
        ResolvedVideoCache(java.io.File(context.filesDir, "local-resolved-videos.json")))
    private val websiteResolver: app.streamsave.parser.VideoResolver = app.streamsave.parser.MiuiSiteResolver(
        app.streamsave.parser.MiuiSiteClient(context.applicationContext)::request,
        PrefixMaskStore(java.io.File(context.filesDir, "media-prefixes")),
        ResolvedVideoCache(java.io.File(context.filesDir, "site-resolved-videos.json")),
        LocalTencentResolver(client, { null }))
    val resolver = app.streamsave.parser.ResolverRouter(preferences.state.value.resolver, websiteResolver, localResolver,
        app.streamsave.parser.WebsiteProbe(client)::check)
    val library = MediaLibrary(context)
    val notifications = DownloadNotifications(context)
    val engine = DownloadEngine(context, history, resolver, HttpTransfer(client), library)
    val scheduler = TransferScheduler(context, history, notifications)
}
