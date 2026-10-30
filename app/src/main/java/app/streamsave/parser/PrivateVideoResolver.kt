package app.streamsave.parser

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class PrivateConnection(val endpoint: String, val token: String) {
    fun url(path: String): HttpUrl = normalizeEndpoint(endpoint).toHttpUrlOrNull()!!.newBuilder().encodedPath(path).build()
    companion object {
        fun normalizeEndpoint(value: String): String {
            val url = value.trim().toHttpUrlOrNull()
            require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                url.query == null && url.fragment == null && url.encodedPath == "/") { "请填写私人服务的 HTTPS 根地址，不要包含路径、参数或密码" }
            return url.toString().trimEnd('/')
        }
    }
}

/** Talks only to the user's HTTPS service. No public parser fallback or local daily counter. */
class PrivateVideoResolver(
    private val client: OkHttpClient,
    private val connection: () -> PrivateConnection?,
    private val cache: ResolvedVideoCache = ResolvedVideoCache(),
) : VideoResolver {
    private val mutex = Mutex()
    private var cacheScope = ""

    override suspend fun resolve(source: String): VideoInfo = withContext(Dispatchers.IO) {
        val link = ShareLink.extract(source) ?: throw ResolveException("没有找到有效的视频号分享链接")
        if (link.startsWith("https://channels.weixin.qq.com/")) return@withContext ChannelsResolver(client).resolve(link)
        mutex.withLock {
            val config = connection() ?: throw ResolveException("请先在设置中连接你的私人解析服务")
            val scope = java.security.MessageDigest.getInstance("SHA-256")
                .digest((config.endpoint + "\n" + config.token).toByteArray()).joinToString("") { "%02x".format(it) }
            if (cacheScope != scope) { cache.useScope(scope); cacheScope = scope }
            cache.get(link)?.let { return@withLock it }
            val request = authorized(config, "/v1/resolve")
                .post(JSONObject().put("url", link).toString().toRequestBody("application/json".toMediaType())).build()
            val video = parseResponse(exchange(request), link)
            cache.put(video)
            video
        }
    }

    suspend fun checkConnection(): String = withContext(Dispatchers.IO) {
        val config = connection() ?: throw ResolveException("请先保存私人服务地址和访问密钥")
        val json = exchange(authorized(config, "/v1/health").get().build())
        if (json.optString("api") != "wx-video-resolver" || json.optInt("version") != 1)
            throw ResolveException("此地址不是兼容的私人解析服务")
        if (!json.optBoolean("sessionConfigured"))
            throw ResolveException("服务已连接，但服务器尚未配置你自己的元宝登录会话")
        "服务已连接。登录会话是否有效，将在解析真实链接时检查。"
    }

    private fun authorized(config: PrivateConnection, path: String) =
        Request.Builder().url(config.url(path)).header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json").header("User-Agent", "WeChatVideoDownload/1.2 Android")

    private suspend fun exchange(request: Request): JSONObject = coroutineScope {
        val call = client.newCall(request)
        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                // Redirects are disabled by the AppGraph client; credentials never follow a redirect.
                if (response.code in 300..399) throw ResolveException("私人服务发生重定向，请填写最终 HTTPS 地址")
                val input = response.body ?: throw ResolveException("私人服务返回空响应")
                val buffered = input.source()
                buffered.request(2L * 1024 * 1024 + 1)
                if (buffered.buffer.size > 2L * 1024 * 1024) throw ResolveException("私人服务响应过大")
                val json = try { JSONObject(buffered.readUtf8()) } catch (_: org.json.JSONException) {
                    throw ResolveException("私人服务返回了非预期数据（HTTP ${response.code}）")
                }
                val code = json.optJSONObject("error")?.optString("code").orEmpty()
                if (!response.isSuccessful || code.isNotBlank()) throw ResolveException(errorMessage(code, response.code))
                json
            }
        } catch (e: java.io.IOException) { currentCoroutineContext().ensureActive(); throw e }
        finally { watcher.cancel() }
    }

    companion object {
        fun parseResponse(json: JSONObject, source: String): VideoInfo {
            val data = json.optJSONObject("video") ?: throw ResolveException("私人服务缺少视频信息")
            if (data.optString("sourceUrl") != source) throw ResolveException("私人服务返回的视频与当前链接不匹配")
            val media = data.optString("videoUrl")
            if (media.isBlank()) throw ResolveException("此链接没有可下载的视频")
            if (!ChannelsResolver.isTencentMedia(media)) throw ResolveException("私人服务未返回可信的腾讯媒体地址")
            return VideoInfo(source, data.optString("title").take(2000).ifBlank { "视频号视频" },
                data.optString("author").take(200), data.optString("coverUrl").takeIf(ChannelsResolver::isTencentMedia).orEmpty(), media)
        }

        fun errorMessage(code: String, status: Int): String = when (code) {
            "UNAUTHORIZED" -> "私人服务访问密钥不正确，请检查设置"
            "SERVER_TOKEN_NOT_CONFIGURED" -> "服务器尚未配置 RESOLVER_TOKEN"
            "UPSTREAM_SESSION_MISSING" -> "服务器尚未配置有效的元宝登录会话"
            "UPSTREAM_SESSION_INVALID" -> "腾讯未接受服务器的登录会话，请更新你自己的元宝 Cookie；这不是解析次数额度"
            "UPSTREAM_PARSE_REJECTED" -> "腾讯未返回视频播放地址，请检查链接和你自己的登录会话；也可能需要更新解析接口"
            "UPSTREAM_RATE_LIMITED" -> "腾讯接口暂时限制请求，请稍后再试；私人服务未设置每日次数"
            "UPSTREAM_TIMEOUT" -> "腾讯接口响应超时，请稍后重试"
            "VIDEO_UNAVAILABLE" -> "此视频暂时无法获取，可能为直播、图集、私密内容或链接已失效"
            "UPSTREAM_SCHEMA_CHANGED", "UNTRUSTED_MEDIA" -> "腾讯接口格式可能已变化，请按维护文档检查私人解析器"
            "INVALID_LINK" -> "分享链接格式不正确"
            else -> if (status == 401) "私人服务访问密钥不正确" else "私人服务暂不可用（HTTP $status），请检查服务运行状态"
        }
    }
}

