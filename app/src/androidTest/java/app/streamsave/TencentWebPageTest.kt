package app.streamsave

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.streamsave.login.TencentLoginActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in page diagnostic on a clean emulator; does not log in, read cookies or enter account data. */
class TencentWebPageTest {
    private fun browser(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) browser(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun inspectOfficialLoginPage(): Unit {
        assumeTrue(InstrumentationRegistry.getArguments().getString("webProbe") == "true")
        val app = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        assumeTrue("This diagnostic is only for a clean, logged-out emulator", !app.graph.tencentSession.state.value.enabled)
        ActivityScenario.launch(TencentLoginActivity::class.java).use { scenario ->
            var result = ""
            repeat(6) {
                val latch = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val web = browser(activity.window.decorView)
                    if (web == null) { latch.countDown(); return@onActivity }
                    web.evaluateJavascript("JSON.stringify({state:document.readyState,title:document.title,text:document.body.innerText.slice(0,1600),scripts:document.scripts.length,html:document.body.innerHTML.slice(0,1200)})") {
                        result = it; latch.countDown()
                    }
                }
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                Thread.sleep(5000)
            }
            val folder = File(app.getExternalFilesDir(null), "verification").apply { mkdirs() }
            File(folder, "web-page-diagnostic.json").writeText(result)
            assertTrue(result.isNotBlank())
        }
    }
}
