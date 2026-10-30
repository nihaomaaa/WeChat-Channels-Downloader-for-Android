package app.streamsave.parser

import kotlinx.coroutines.*
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HTTP reachability, not ICMP: checks the exact page used by MiuiSiteClient.
 * Sends no share link, credentials or account cookies. Does not follow redirects. */
class WebsiteProbe(client: OkHttpClient, private val endpoint: String = "https://sph.miuistore.com/") {
    private val transport = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).cache(null)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()

    suspend fun check(): WebsiteCheck = withContext(Dispatchers.IO) {
        val call = transport.newCall(Request.Builder().url(endpoint)
            .header("User-Agent", ChannelsResolver.USER_AGENT).header("Cache-Control", "no-cache").get().build())
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return@use WebsiteCheck(false, "下载网站返回 HTTP " + response.code)
                val body = response.body ?: return@use WebsiteCheck(false, "下载网站返回空页面")
                val source = body.source()
                source.request(32768)
                val html = source.readUtf8(minOf(source.buffer.size, 32768L))
                val looksUsable = body.contentType()?.subtype == "html" &&
                    html.contains("<html", ignoreCase = true) &&
                    (html.contains("视频号") || html.contains("search-input"))
                WebsiteCheck(looksUsable, if (looksUsable) "下载网站可访问" else "下载网站没有返回可识别的页面")
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            WebsiteCheck(false, when (e) {
                is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "下载网站连接超时"
                is javax.net.ssl.SSLException -> "下载网站安全连接失败"
                else -> "无法连接下载网站，请检查网络"
            })
        } finally { cancellation.cancel() }
    }
}
