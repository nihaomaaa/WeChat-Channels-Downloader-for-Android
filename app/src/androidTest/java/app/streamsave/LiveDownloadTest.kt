package app.streamsave

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.streamsave.data.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in real network test; no prerecorded response or hardcoded media address.
 * Invoked with instrumentation argument live=true. Keeps the downloaded video for visual QA. */
class LiveDownloadTest {
    @Test fun resolveActualShareDownloadAndDecodeFromMediaStore(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("live") == "true")
        val app = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        val graph = app.graph

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(1000)
            val video = graph.resolver.resolve("https://weixin.qq.com/sph/AKQAcoYOYg")
            assertTrue(video.downloadable)
            assertTrue(video.title.isNotBlank())
            val row = graph.scheduler.enqueue(video)
            withTimeout(300_000) { while (graph.history.get(row.id)?.active == true) delay(300) }
            val saved = graph.history.get(row.id)!!
            assertEquals(saved.error, DownloadStatus.COMPLETE, saved.status)
            assertTrue(saved.bytes > 1_000_000)
            assertTrue(graph.library.exists(saved.uri))
            val evidence = File(app.getExternalFilesDir(null), "verification").apply { mkdirs() }
            var duration = ""
            MediaMetadataRetriever().use { decoder ->
                decoder.setDataSource(app, Uri.parse(saved.uri))
                duration = decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).orEmpty()
                val frame = decoder.getFrameAtTime(3_000_000)
                assertNotNull(frame)
                File(evidence, "actual-video-frame.jpg").outputStream().use { frame!!.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                frame?.recycle()
            }
            File(evidence, "live-download.json").writeText(JSONObject()
                .put("checkedAt", System.currentTimeMillis()).put("title", saved.video.title).put("author", saved.video.author)
                .put("bytes", saved.bytes).put("durationMs", duration).put("status", saved.status.name)
                .put("mediaUri", saved.uri).put("decodedFrame", true).put("resolver", "miuistore-public-site")
                .put("androidSdk", android.os.Build.VERSION.SDK_INT).toString(2))
        } finally { scenario.close() }
    }
}
