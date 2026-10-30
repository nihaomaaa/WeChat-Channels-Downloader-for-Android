package app.streamsave

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.streamsave.data.*
import app.streamsave.download.*
import app.streamsave.parser.VideoInfo
import app.streamsave.parser.VideoResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun actualMp4DownloadsPublishesDecodesAndDeletes() = runBlocking {
        val server = MockWebServer(); server.start()
        val fixture = FixtureVideo.create(File(context.cacheDir, "generated-test.mp4"))
        val library = MediaLibrary(context)
        val history = HistoryStore(context)
        val expected = fixture.readBytes()
        val video = VideoInfo("instrumented-fixture", "测试专用 · 本机生成的视频", videoUrl = server.url("/actual.mp4").toString())
        val engine = DownloadEngine(context, history, object : VideoResolver { override suspend fun resolve(source: String) = video }, HttpTransfer(OkHttpClient()), library)
        val row = history.enqueue(video)
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"fixture-v1\"").setBody(Buffer().write(expected)))
            engine.run(row.id) {}
            val completed = history.get(row.id)!!
            assertEquals(completed.error, DownloadStatus.COMPLETE, completed.status)
            assertEquals(expected.size.toLong(), completed.bytes)
            assertTrue(library.exists(completed.uri))
            val uri = Uri.parse(completed.uri)
            assertArrayEquals(expected, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.IS_PENDING, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.RELATIVE_PATH), null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals("video/mp4", it.getString(1)); assertEquals("Movies/微信视频号下载/", it.getString(2))
            }
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                assertNotNull(retriever.getFrameAtTime(200_000))
            }
            library.delete(completed.uri)
            assertFalse(library.exists(completed.uri))
        } finally {
            history.get(row.id)?.uri?.takeIf { it.isNotEmpty() }?.let(library::delete)
            history.delete(row.id); history.close(); fixture.delete(); server.shutdown()
        }
    }

    @Test fun htmlErrorCannotEnterGalleryAndDuplicateClickSharesTask(): Unit = runBlocking {
        val server = MockWebServer(); server.start()
        val history = HistoryStore(context)
        val library = MediaLibrary(context)
        val video = VideoInfo("bad-fixture", "非法响应测试", videoUrl = server.url("/error").toString())
        val first = history.enqueue(video)
        assertEquals(first.id, history.enqueue(video).id)
        try {
            server.enqueue(MockResponse().setBody("<html>Expired token, please sign in</html>"))
            val engine = DownloadEngine(context, history, object : VideoResolver { override suspend fun resolve(source: String) = video }, HttpTransfer(OkHttpClient()), library)
            engine.run(first.id) {}
            val failed = history.get(first.id)!!
            assertEquals(DownloadStatus.FAILED, failed.status)
            assertEquals("", failed.uri)
            assertTrue(failed.error.contains("MP4"))
            engine.partial(first.id).delete()
        } finally { history.delete(first.id); history.close(); server.shutdown() }
    }

    @Test fun upgradeDiscardsLegacyCredentialsAndKeepsTheme() {
        val raw = context.getSharedPreferences("preferences", 0)
        raw.edit().putString("cookie", "obsolete_encrypted_test_value").putInt("theme", 2).commit()
        val preferences = Preferences(context)
        assertFalse(raw.contains("cookie"))
        assertEquals(2, preferences.state.value.theme)
        preferences.theme(0)
    }
    @Test fun privateServiceTokenIsEncryptedAndEndpointIsStrict() {
        val settings = app.streamsave.data.PrivateServiceSettings(context)
        val raw = context.getSharedPreferences("private_service", 0)
        val token = "test-only-access-token-1234567890abcdef"
        try {
            settings.save("https://resolver.example/", token)
            assertFalse(raw.all.toString().contains(token))
            assertEquals(token, settings.connection()!!.token)
            assertEquals("https://resolver.example", settings.connection()!!.endpoint)
            assertThrows(IllegalArgumentException::class.java) { settings.save("http://resolver.example", token) }
            assertThrows(IllegalArgumentException::class.java) { settings.save("https://different.example", "") }
        } finally { settings.clear() }
        assertNull(settings.connection())
    }
}

