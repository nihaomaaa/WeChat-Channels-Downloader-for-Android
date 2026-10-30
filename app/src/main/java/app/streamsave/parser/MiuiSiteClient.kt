package app.streamsave.parser

import android.content.Context
import android.webkit.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** Loads the site's public client without copying its signing code or exposing a native bridge. */
class MiuiSiteClient(private val context: Context) {
    suspend fun request(id: String): JSONObject = try {
        withTimeout(75_000) { withContext(Dispatchers.Main) {
            val web = WebView(context)
            var failure: String? = null
            try {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.allowFileAccess = false
                web.settings.allowContentAccess = false
                web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
                web.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                        if (r.url.scheme == "https" && r.url.host == "sph.miuistore.com") null
                        else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                        if (r.isForMainFrame) failure = "无法连接解析网站，请检查网络后重试"
                    }
                    override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: android.net.http.SslError) {
                        h.cancel(); failure = "解析网站安全连接验证失败"
                    }
                    override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
                        failure = "网页组件已停止，请更新系统网页组件后重试"; return true
                    }
                }
                web.loadUrl("https://sph.miuistore.com/")
                while (web.eval("typeof AlgoSign === 'function'") != "true") {
                    failure?.let { throw ResolveException(it) }; delay(250)
                }
                web.eval(SCRIPT.replace("__ID__", JSONObject.quote(id)))
                var result: JSONObject? = null
                while (result == null) {
                    failure?.let { throw ResolveException(it) }
                    val encoded = web.eval("window.__streamsaveResult || null")
                    if (encoded != "null") {
                        if (encoded.length > 3 * 1024 * 1024) throw ResolveException("网站响应过大")
                        result = JSONObject(JSONTokener(encoded).nextValue() as String)
                    } else delay(250)
                }
                if (result.has("failure")) throw ResolveException("网站暂未完成解析，请稍后重试")
                if (result.optInt("http") != 200) throw ResolveException("解析网站暂不可用（HTTP " + result.optInt("http") + "）")
                result.getJSONObject("body")
            } finally { web.stopLoading(); web.destroy() }
        } }
    } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); throw ResolveException("网站解析超时，请稍后重试") }

    private suspend fun WebView.eval(code: String): String = suspendCancellableCoroutine { c ->
        evaluateJavascript(code) { if (c.isActive) c.resume(it) { _, _, _ -> } }
    }
    companion object {
        private val SCRIPT = """
            (function(){
                window.__streamsaveResult=null;
                (async function(){
                    try {
                        const id=__ID__;
                        const s=await new AlgoSign({appId:'sph'}).sign({id:id,path:'/sph/public/quick'});
                        const r=await fetch('/sph/public/quick?id='+encodeURIComponent(id)+'&sign='+encodeURIComponent(s._sign)+'&_from=&title=');
                        const reader=r.body.getReader(), decoder=new TextDecoder();
                        let text='', count=0;
                        while(true){const p=await reader.read();if(p.done)break;count+=p.value.length;
                            if(count>1048576){await reader.cancel();throw new Error('size');}
                            text+=decoder.decode(p.value,{stream:true});}
                        text+=decoder.decode();
                        window.__streamsaveResult=JSON.stringify({http:r.status,body:JSON.parse(text)});
                    }catch(e){window.__streamsaveResult=JSON.stringify({failure:true});}
                })();return true;
            })()
        """.trimIndent()
    }
}
