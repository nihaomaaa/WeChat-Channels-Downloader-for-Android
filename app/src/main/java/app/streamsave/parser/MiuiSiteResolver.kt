package app.streamsave.parser

import app.streamsave.download.PrefixMaskStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException

class MiuiSiteResolver(
    private val request: suspend (String) -> JSONObject,
    private val masks: PrefixMaskStore,
    private val cache: ResolvedVideoCache,
    private val preview: VideoResolver,
) : VideoResolver {
    private val mutex = Mutex()
    override suspend fun resolve(source: String): VideoInfo = withContext(Dispatchers.IO) {
        val link = ShareLink.extract(source) ?: throw ResolveException("没有找到有效的视频号分享链接")
        if (link.toHttpUrl().host == "channels.weixin.qq.com") return@withContext preview.resolve(link)
        mutex.withLock {
            cache.useScope("miui-site-v1")
            cache.get(link)?.takeIf { masks.exists(it.prefixId) }?.let { return@withLock it }
            val video = parse(request(link.toHttpUrl().pathSegments.last() + "##1"), link, masks)
            val extra = withTimeoutOrNull(5000) { try { preview.resolve(link) } catch (_: IOException) { null } }
            video.copy(author = extra?.author.orEmpty(), coverUrl = extra?.coverUrl.orEmpty()).also(cache::put)
        }
    }
    companion object {
        fun parse(json: JSONObject, source: String, masks: PrefixMaskStore): VideoInfo {
            if (json.optInt("error", -1) != 0) throw ResolveException("网站解析失败：" +
                json.optString("message", "请稍后重试").replace(Regex("<[^>]*>"), "").take(180))
            val media = json.optJSONObject("media") ?: throw ResolveException("网站没有返回视频信息")
            if (media.optInt("is_live") != 0) throw ResolveException("暂不支持直播链接")
            val url = json.optString("url")
            if (!ChannelsResolver.isTencentMedia(url)) throw ResolveException("网站未返回可信的腾讯视频地址")
            if (json.optString("enurl").isNotBlank()) throw ResolveException("网站返回特殊格式，请在原网站下载")
            if (!json.has("_data") || json.isNull("_data")) throw ResolveException("网站返回数据不完整")
            return VideoInfo(source, media.optString("title").ifBlank { "视频号视频" }.take(2000),
                videoUrl = url, prefixId = masks.put(json.getString("_data")), notice = "由 sph.miuistore.com 提供解析")
        }
    }
}
