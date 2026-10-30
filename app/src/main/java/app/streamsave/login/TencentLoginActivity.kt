package app.streamsave.login

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.streamsave.StreamSaveApp
import app.streamsave.data.TencentSession
import app.streamsave.ui.Caption
import app.streamsave.ui.SecondaryAction
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.*

/** Official Tencent pages in an isolated app-owned browser. No JS bridge or credential form. */
class TencentLoginActivity : ComponentActivity() {
    private var browser: WebView? = null
    private var loading by mutableStateOf(true)
    private var message by mutableStateOf("")
    private var broken by mutableStateOf(false)
    private var needsUpdate by mutableStateOf(false)
    private var host by mutableStateOf("yuanbao.tencent.com")
    private val graph get() = (application as StreamSaveApp).graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val setting = graph.preferences.state.collectAsState().value
            val dark = setting.theme == 2 || setting.theme == 0 && isSystemInDarkTheme()
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onBackground) {
                    BackHandler { if (browser?.canGoBack() == true) browser?.goBack() else finish() }
                    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).systemBarsPadding().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SecondaryAction("返回", { finish() })
                            Column(Modifier.weight(1f)) { Text("腾讯官方网页登录", fontSize = 17.sp); Caption(host) }
                        }
                        Caption("在下方官方页面登录。无需复制 Cookie，登录信息仅留在本机。", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (!broken) AndroidView(factory = { createBrowser() }, modifier = Modifier.fillMaxSize())
                        }
                        if (message.isNotBlank()) Caption(message, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        if (needsUpdate) SecondaryAction("打开应用商店更新", { updateBrowser() }, Modifier.padding(horizontal = 12.dp))
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryAction("重新加载", {
                                message = ""; loading = true
                                if (broken) broken = false else browser?.reload()
                            })
                            SecondaryAction("完成登录，返回", { complete() }, Modifier.weight(1f), enabled = !broken)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): android.view.View {
        return try {
            val major = WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull()
            if (major != null && major < 100) {
                loading = false; broken = true; needsUpdate = true
                message = "手机的网页组件版本过旧，请在应用商店更新 Android System WebView 后再登录。"
                return android.widget.TextView(this)
            }
            WebView(this).apply web@{
                browser = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = TencentSession.DEFAULT_AGENT
                CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(this@web, true) }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (TencentPagePolicy.allows(uri.toString())) return false
                        if (request.hasGesture() && uri.scheme == "weixin") {
                            try { startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.tencent.mm")) }
                            catch (_: ActivityNotFoundException) { message = "本机没有微信，请使用网页提供的其他登录方式" }
                            catch (_: SecurityException) { message = "系统未允许打开微信，请使用网页登录" }
                        } else message = "该跳转无法在此打开，请使用当前腾讯网页中的登录方式"
                        return true
                    }
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        if (!TencentPagePolicy.allows(url)) { view.stopLoading(); message = "已停止打开非腾讯登录页面"; return }
                        host = Uri.parse(url).host.orEmpty(); loading = true
                    }
                    override fun onPageFinished(view: WebView, url: String) { loading = false }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) { loading = false; message = "腾讯网页加载失败，请检查网络后重新加载" }
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        if (request.isForMainFrame) { loading = false; message = "腾讯网页暂不可用（HTTP ${errorResponse.statusCode}）" }
                    }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel(); loading = false; message = "网页证书校验失败，已停止连接，请检查手机时间和网络"
                    }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        (view.parent as? ViewGroup)?.removeView(view); view.destroy(); browser = null
                        broken = true; loading = false; message = "系统网页进程已退出，请重新加载"
                        return true
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(TencentSession.ORIGIN)
            }
        } catch (_: android.util.AndroidRuntimeException) {
            loading = false; broken = true; message = "系统网页组件不可用，请更新 Android System WebView 后重试"
            android.widget.TextView(this)
        }
    }

    private fun complete() {
        val view = browser ?: return
        if (CookieManager.getInstance().getCookie(TencentSession.ORIGIN).isNullOrBlank()) {
            message = "尚未取得网页会话，请先在腾讯页面完成登录"; return
        }
        // A cookie is not proof of login. Only an actual successful parse marks the session verified.
        graph.tencentSession.enable(view.settings.userAgentString)
        setResult(RESULT_OK); finish()
    }
    private fun updateBrowser() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))) }
        catch (_: ActivityNotFoundException) { message = "请在手机应用商店搜索 Android System WebView，或检查系统更新" }
        catch (_: SecurityException) { message = "系统未允许打开应用商店，请手动检查网页组件更新" }
    }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onDestroy() {
        browser?.let { (it.parent as? ViewGroup)?.removeView(it); it.stopLoading(); it.destroy() }
        browser = null; super.onDestroy()
    }
}
