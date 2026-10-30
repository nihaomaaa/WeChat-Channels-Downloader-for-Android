package app.streamsave.login

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TencentPagePolicy {
    fun allows(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            (url.host == "tencent.com" || url.host.endsWith(".tencent.com") ||
                url.host == "qq.com" || url.host.endsWith(".qq.com"))
    }
}
