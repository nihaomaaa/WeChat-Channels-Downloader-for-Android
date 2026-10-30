package app.streamsave.data

import android.content.Context
import android.webkit.CookieManager
import app.streamsave.parser.TencentCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class TencentLoginState(val enabled: Boolean = false, val verified: Boolean = false, val expired: Boolean = false)

/** This app's WebView cookies only. No WeChat or external-browser data access. */
class TencentSession(context: Context) {
    private val prefs = context.getSharedPreferences("tencent_web_session", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(readState())
    val state = mutable.asStateFlow()
    private fun readState() = TencentLoginState(prefs.getBoolean("enabled", false),
        prefs.getBoolean("verified", false), prefs.getBoolean("expired", false))
    private fun revision() = prefs.getLong("revision", 0)

    suspend fun credentials(): TencentCredentials? = withContext(Dispatchers.Main.immediate) {
        if (!mutable.value.enabled) return@withContext null
        val cookies = try { CookieManager.getInstance().getCookie(ORIGIN).orEmpty() }
        catch (_: android.util.AndroidRuntimeException) { throw java.io.IOException("系统网页组件不可用，请更新 Android System WebView 后重试") }
        if (cookies.isBlank()) return@withContext null
        TencentCredentials(cookies, prefs.getString("agent", DEFAULT_AGENT).orEmpty(), revision())
    }

    @Synchronized fun enable(userAgent: String) {
        CookieManager.getInstance().flush()
        prefs.edit().putBoolean("enabled", true).putBoolean("verified", false).putBoolean("expired", false)
            .putLong("revision", revision() + 1).putString("agent", userAgent).apply()
        mutable.value = readState()
    }
    @Synchronized fun accepted(expectedRevision: Long) {
        if (revision() != expectedRevision || !mutable.value.enabled) return
        prefs.edit().putBoolean("verified", true).putBoolean("expired", false).apply()
        mutable.value = readState()
    }
    @Synchronized fun rejected(expectedRevision: Long) {
        if (revision() != expectedRevision || !mutable.value.enabled) return
        prefs.edit().putBoolean("verified", false).putBoolean("expired", true).apply()
        mutable.value = readState()
    }
    suspend fun clear() = withContext(Dispatchers.Main.immediate) {
        synchronized(this@TencentSession) {
            prefs.edit().putBoolean("enabled", false).putBoolean("verified", false).putBoolean("expired", false)
                .putLong("revision", revision() + 1).remove("agent").apply()
            mutable.value = readState()
        }
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
        }
        CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
    }
    companion object {
        const val ORIGIN = "https://yuanbao.tencent.com/"
        const val DEFAULT_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
