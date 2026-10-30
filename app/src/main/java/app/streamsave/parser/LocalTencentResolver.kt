package app.streamsave.parser

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

/** Credentials belong to this app's Tencent web login; never log or export them. */
class TencentCredentials(val cookie: String, val userAgent: String, val revision: Long) {
    override fun toString() = "TencentCredentials(redacted)"
}
class TencentLoginRequired : java.io.IOException("请在 App 内登录腾讯元宝，然后返回重试；无需配置服务器或复制 Cookie")

/** Direct fixed-host Tencent requests. No private/public parser server. */
class LocalTencentResolver(
    client: OkHttpClient,
    private val credentials: suspend () -> TencentCredentials?,
    private val accepted: (Long) -> Unit = {},
    private val rejected: (Long) -> Unit = {},
    private val cache: ResolvedVideoCache = ResolvedVideoCache(),
) : VideoResolver {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val mutex = Mutex()

    override suspend fun resolve(source: String): VideoInfo = withContext(Dispatchers.IO) {
        val link = ShareLink.extract(source) ?: throw ResolveException("没有找到有效的视频号分享链接")
        mutex.withLock {
            if (link.startsWith("https://channels.weixin.qq.com/")) {
                val video = ChannelsResolver(client).resolve(link)
                if (!video.downloadable) throw ResolveException("此播放链接没有视频地址，请重新复制分享链接")
                return@withLock video
            }
            val session = credentials()
            val scope = session?.let {
                MessageDigest.getInstance("SHA-256").digest((it.revision.toString()+it.cookie).toByteArray())
                    .joinToString("") { value -> "%02x".format(value) }
            } ?: "anonymous-v1"
            cache.useScope(scope)
            cache.get(link)?.let { return@withLock it }
            val shortUri = link.toHttpUrl().pathSegments.last { it.isNotBlank() }
            val preview = ChannelsResolver.parseFeed(post(FEED,
                JSONObject().put("baseReq", JSONObject().put("generalToken", "")).put("shortUri", shortUri)), link)
            if (preview.downloadable) { cache.put(preview); return@withLock preview }
            if (session == null || session.cookie.isBlank()) return@withLock preview.copy(
                notice = "已找到视频，但腾讯尚未提供下载地址。请先在 App 内登录腾讯元宝，无需复制 Cookie。", needsLogin = true)

            val parsed = try {
                post(PARSE, JSONObject().put("type", "video_channel_url").put("url", link).put("scene", 1), session)
            } catch (e: TencentLoginRequired) { rejected(session.revision); throw e }
            if ((parsed.has("error") && !parsed.isNull("error")) || (parsed.has("code") && parsed.optInt("code") != 0))
                throw ResolveException("腾讯暂未接受此解析请求。可重新登录后重试；也可能是链接或腾讯接口发生变化，并非本应用的次数额度")
            val playable = parsed.optJSONObject("data")?.optString("playable_url")?.toHttpUrlOrNull()
            if (playable == null || !playable.isHttps || playable.host != "channels.weixin.qq.com" ||
                playable.port != 443 || playable.username.isNotEmpty() || playable.password.isNotEmpty())
                throw ResolveException("腾讯未返回可用的播放链接，请确认已在网页登录成功后重试")
            val token = playable.queryParameter("token").orEmpty()
            val eid = playable.queryParameter("eid").orEmpty()
            if (token.isBlank() || eid.isBlank()) throw ResolveException("腾讯播放链接格式已变化，需要更新内置解析器")
            // Do not pass the Yuanbao session to Channels or the video CDN.
            val result = ChannelsResolver.parseFeed(post(FEED,
                JSONObject().put("baseReq", JSONObject().put("generalToken", token)).put("exportId", eid), referer = playable.toString()), link)
            if (!result.downloadable) throw ResolveException("腾讯返回了视频信息，但没有提供可下载的视频地址")
            accepted(session.revision)
            cache.put(result)
            result
        }
    }

    private suspend fun post(url: String, body: JSONObject, session: TencentCredentials? = null,
                             referer: String = "https://channels.weixin.qq.com/finder-preview/pages/feed"): JSONObject = coroutineScope {
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", session?.userAgent ?: ChannelsResolver.USER_AGENT)
            .header("Origin", if (url == PARSE) "https://yuanbao.tencent.com" else "https://channels.weixin.qq.com")
            .header("Referer", if (url == PARSE) "https://yuanbao.tencent.com/" else referer)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (session != null) { check(url == PARSE); request.header("Cookie", session.cookie) }
        val call = client.newCall(request.build())
        val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                if (url == PARSE && response.code in listOf(401, 403)) throw TencentLoginRequired()
                if (response.code == 429) throw ResolveException("腾讯暂时限制请求，请稍后重试。本应用没有每日次数限制")
                if (response.code in 300..399) throw ResolveException("腾讯接口发生重定向，已停止传递登录信息，请更新解析器")
                if (!response.isSuccessful) throw ResolveException("腾讯接口暂不可用（HTTP ${response.code}）")
                val input = response.body?.source() ?: throw ResolveException("腾讯接口返回空响应")
                input.request(2L * 1024 * 1024 + 1)
                if (input.buffer.size > 2L * 1024 * 1024) throw ResolveException("腾讯接口响应过大")
                try { JSONObject(input.readUtf8()) }
                catch (_: org.json.JSONException) { throw ResolveException("腾讯接口未返回有效视频信息，请稍后重试") }
            }
        } catch (e: java.io.IOException) { currentCoroutineContext().ensureActive(); throw e }
        finally { cancellation.cancel() }
    }

    companion object {
        const val PARSE = "https://yuanbao.tencent.com/api/weixin/get_parse_result"
        const val FEED = ChannelsResolver.FEED
    }
}
