package app.streamsave

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.streamsave.parser.ChannelsResolver
import app.streamsave.parser.LocalTencentResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in anonymous real Tencent request; metadata success is NOT a successful video download. */
class LocalMetadataLiveTest {
    @Test fun recordsWhatTencentActuallyReturnsWithoutAnAccount(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("metadataLive") == "true")
        val context = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        val resolver = LocalTencentResolver(OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(), { null })
        val result = resolver.resolve("https://weixin.qq.com/sph/AKQAcoYOYg")
        assertTrue(result.title.isNotBlank()); assertTrue(result.author.isNotBlank())
        assertTrue(result.downloadable || result.needsLogin)
        if (result.downloadable) assertTrue(ChannelsResolver.isTencentMedia(result.videoUrl))
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val folder = (output?.let { File(it) } ?: File(context.getExternalFilesDir(null), "verification")).apply { mkdirs() }
        File(folder, "direct-metadata.json").writeText(JSONObject().put("title", result.title).put("author", result.author)
            .put("hasVideoUrl", result.downloadable).put("needsLogin", result.needsLogin)
            .put("apiLevel", android.os.Build.VERSION.SDK_INT).put("cookiesSent", false)
            .put("fullDownloadVerified", false).toString(2))
    }
}
