package app.streamsave

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in inspection of the user-supplied public website, never enters credentials or pays. */
class MiuiSiteProbeTest {
    @Test fun inspectPublicParseResponse() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("siteProbe") == "true")
        val app = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        var web: WebView? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    web = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = WebViewClient()
                    }
                    activity.setContentView(web)
                    web!!.loadUrl("https://sph.miuistore.com/")
                }
                fun evaluate(script: String): String {
                    val latch = CountDownLatch(1)
                    var value = ""
                    scenario.onActivity { web!!.evaluateJavascript(script) { value = it; latch.countDown() } }
                    assertTrue(latch.await(10, TimeUnit.SECONDS))
                    return value
                }
                var ready = false
                repeat(30) {
                    if (!ready) {
                        ready = evaluate("typeof AlgoSign === 'function'") == "true"
                        if (!ready) Thread.sleep(1000)
                    }
                }
                assertTrue("Website signing script did not load", ready)
                evaluate("""(function(){
                    window.__probeResult = null;
                    (async function(){
                        try {
                            const id = 'AKQAcoYOYg##1';
                            const sign = (await new AlgoSign({appId:'sph'}).sign({id:id,path:'/sph/public/quick'}))._sign;
                            const response = await fetch('/sph/public/quick?id='+encodeURIComponent(id)+'&sign='+encodeURIComponent(sign)+'&_from=&title=');
                            window.__probeResult = JSON.stringify({http:response.status, body:await response.json()});
                        } catch(e) { window.__probeResult = JSON.stringify({failure:String(e)}); }
                    })();
                    return true;
                })()""")
                var result = "null"
                repeat(90) {
                    if (result == "null") {
                        result = evaluate("window.__probeResult")
                        if (result == "null") Thread.sleep(1000)
                    }
                }
                assertNotEquals("Website did not answer", "null", result)
                val decoded = JSONTokener(result).nextValue().toString()
                val folder = File(app.getExternalFilesDir(null), "verification").apply { mkdirs() }
                File(folder, "miui-site-response.json").writeText(decoded)
                assertTrue(JSONObject(decoded).has("body"))
            } finally { scenario.onActivity { web?.destroy() } }
        }
    }
}

