package app.streamsave.parser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Parse shared text, not arbitrary URLs. Host checks are exact, before any network request. */
object ShareLink {
    private val urls = Regex("https?://[^\\s<>\"'，。；！？）】]+", RegexOption.IGNORE_CASE)
    fun extract(text: String): String? = urls.findAll(text.take(16384)).mapNotNull { match ->
        val url = match.value.trimEnd(')', ']', '}', '.', ',', ';').toHttpUrlOrNull() ?: return@mapNotNull null
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != (if (url.isHttps) 443 else 80)) return@mapNotNull null
        when {
            url.host == "weixin.qq.com" && Regex("/sph/[A-Za-z0-9_-]{4,128}/?").matches(url.encodedPath) ->
                "https://weixin.qq.com/sph/${url.pathSegments[1]}"
            url.host == "channels.weixin.qq.com" && url.encodedPath == "/finder-preview/pages/sph" ->
                url.queryParameter("id")?.takeIf { Regex("[A-Za-z0-9_-]{4,128}").matches(it) }?.let { "https://weixin.qq.com/sph/$it" }
            url.host == "channels.weixin.qq.com" && url.encodedPath == "/finder-preview/pages/feed" &&
                !url.queryParameter("eid").isNullOrBlank() && !url.queryParameter("token").isNullOrBlank() ->
                url.newBuilder().scheme("https").port(443).fragment(null).build().toString()
            else -> null
        }
    }.firstOrNull()
}
