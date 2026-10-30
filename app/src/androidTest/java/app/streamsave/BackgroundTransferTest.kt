package app.streamsave

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.streamsave.data.DownloadStatus
import app.streamsave.parser.VideoInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundTransferTest {
    @get:Rule val permission: GrantPermissionRule = if (android.os.Build.VERSION.SDK_INT >= 33)
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()
    @Test fun userInitiatedDownloadContinuesAfterActivityStops(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        val graph = app.graph
        val fixture = FixtureVideo.create(File(app.cacheDir, "background-test.mp4"))
        // A valid ISO-BMFF free box adds 8 MiB to exercise sustained streaming and notification updates.
        DataOutputStream(FileOutputStream(fixture, true)).use { out ->
            val length = 8 * 1024 * 1024
            out.writeInt(length + 8); out.writeBytes("free")
            repeat(128) { out.write(ByteArray(65536)) }
        }
        val server = MockWebServer(); server.start()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var id = -1L
        try {
            delay(1000)
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"background-fixture\"")
                .setBody(Buffer().readFrom(fixture.inputStream())).throttleBody(128 * 1024, 100, TimeUnit.MILLISECONDS))
            val row = graph.scheduler.enqueue(VideoInfo("background-fixture", "后台下载集成测试", videoUrl = server.url("/video").toString()))
            id = row.id
            withTimeout(25_000) { while (graph.history.get(id)?.status != DownloadStatus.DOWNLOADING) delay(100) }
            scenario.moveToState(Lifecycle.State.CREATED)
            withTimeout(35_000) { while (graph.history.get(id)?.active == true) delay(200) }
            val completed = graph.history.get(id)!!
            assertEquals(completed.error, DownloadStatus.COMPLETE, completed.status)
            assertEquals(fixture.length(), completed.bytes)
            assertTrue(graph.library.exists(completed.uri))
        } finally {
            if (id > 0) {
                graph.scheduler.cancel(id)
                graph.history.get(id)?.uri?.takeIf { it.isNotBlank() }?.let(graph.library::delete)
                graph.history.delete(id); graph.engine.partial(id).delete()
            }
            scenario.close(); server.shutdown(); fixture.delete()
        }
    }
}
