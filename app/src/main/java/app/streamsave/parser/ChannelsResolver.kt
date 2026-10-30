package app.streamsave.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** Tencent preview links carrying their own token/eid; SPH links use PrivateVideoResolver. */
class ChannelsResolver(private val client: OkHttpClient) : VideoResolver {
    override suspend fun resolve(source: String): VideoInfo = withContext(Dispatchers.IO) {
        val link = ShareLink.extract(source) ?: throw ResolveException("没有找到有效的视频号分享链接")
        val url = link.toHttpUrl()
        if (url.host != "channels.weixin.qq.com") throw ResolveException("此分享链接需要在线解析")
        parseFeed(feed(url.queryParameter("eid")!!, url.queryParameter("token")!!), link)
    }

    private fun feed(eid: String, token: String): JSONObject {
        val ref = "https://channels.weixin.qq.com/finder-preview/pages/feed".toHttpUrl().newBuilder()
            .addQueryParameter("token", token).addQueryParameter("eid", eid).build().toString()
        val endpoint = FEED.toHttpUrl().newBuilder()
            .addQueryParameter("_rid", "${(System.currentTimeMillis() / 1000).toString(16)}-${UUID.randomUUID().toString().take(8)}")
            .addQueryParameter("_pageUrl", "https://channels.weixin.qq.com/finder-preview/pages/feed").build().toString()
        return post(endpoint, JSONObject().put("baseReq", JSONObject().put("generalToken", token)).put("exportId", eid), ref)
    }

    private fun post(url: String, body: JSONObject, referer: String): JSONObject {
        val request = Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT).header("Accept", "application/json")
            .header("Origin", "https://channels.weixin.qq.com")
            .header("Referer", referer)
        client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) throw ResolveException(when (response.code) {
                401, 403 -> "此播放链接已失效，请重新复制视频号分享链接"
                429 -> "请求过于频繁，请稍后再试"
                else -> "解析服务暂不可用（HTTP ${response.code}）"
            })
            val input = response.body ?: throw ResolveException("解析服务返回空响应")
            if (input.contentLength() > 2 * 1024 * 1024) throw ResolveException("解析响应过大")
            val buffered = input.source()
            buffered.request(2L * 1024 * 1024 + 1)
            if (buffered.buffer.size > 2L * 1024 * 1024) throw ResolveException("解析响应过大")
            val bytes = buffered.readByteArray()
            return try { JSONObject(String(bytes, Charsets.UTF_8)) }
            catch (_: org.json.JSONException) { throw ResolveException("解析服务返回非预期数据，请稍后重试") }
        }
    }

    companion object {
        const val FEED = "https://channels.weixin.qq.com/finder-preview/api/feed/get_feed_info"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        fun parseFeed(json: JSONObject, source: String): VideoInfo {
            if (json.optInt("errCode", 0) != 0) throw ResolveException("视频信息获取失败（${json.optInt("errCode")}），请稍后重试")
            val data = json.optJSONObject("data") ?: throw ResolveException("解析结果缺少视频信息")
            val error = data.optJSONObject("errMsg")
            if (error != null && (error.optInt("type") != 0 || error.optString("title").isNotBlank())) {
                throw ResolveException(error.optString("title", "此内容暂时无法播放").replace(Regex("<[^>]*>"), "").take(180))
            }
            val feed = data.optJSONObject("feedInfo") ?: throw ResolveException("此链接没有可用的视频信息")
            val media = sequenceOf(feed.optJSONObject("h264VideoInfo")?.optString("videoUrl"),
                feed.optString("videoUrl"), feed.optJSONObject("h265VideoInfo")?.optString("videoUrl"))
                .filterNotNull().firstOrNull { it.isNotBlank() }.orEmpty()
            if (media.isNotEmpty() && !isTencentMedia(media)) throw ResolveException("解析结果包含不受信任的视频地址")
            return VideoInfo(source, feed.optString("description").ifBlank { "视频号视频" }.take(2000),
                data.optJSONObject("authorInfo")?.optString("nickname").orEmpty().take(200),
                feed.optString("coverUrl").takeIf(::isTencentMedia).orEmpty(), media)
        }

        fun isTencentMedia(raw: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                (url.host == "qq.com" || url.host.endsWith(".qq.com") || url.host.endsWith(".qpic.cn"))
        }
    }
}
