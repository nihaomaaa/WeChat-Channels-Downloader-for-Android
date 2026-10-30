package app.streamsave.parser

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Small private cache survives Activity/process restarts without re-spending a service request.
 * URLs are short-lived: only five minutes, never used to claim permanent media availability. */
class ResolvedVideoCache(private val file: File? = null, private val now: () -> Long = System::currentTimeMillis) {
    private data class Entry(val time: Long, val video: VideoInfo)
    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false
    private var scope = ""

    @Synchronized fun useScope(value: String) {
        if (scope == value) return
        scope = value
        entries.clear()
        loaded = false
    }

    @Synchronized fun get(source: String): VideoInfo? {
        load()
        val entry = entries[source] ?: return null
        if (now() - entry.time !in 0 until TTL) { entries.remove(source); return null }
        return entry.video
    }

    @Synchronized fun put(video: VideoInfo) {
        load()
        entries.entries.removeAll { now() - it.value.time !in 0 until TTL }
        entries[video.sourceUrl] = Entry(now(), video)
        while (entries.size > 16) entries.remove(entries.keys.first())
        if (file == null) return
        val values = JSONArray()
        entries.values.forEach { entry ->
            values.put(JSONObject().put("time", entry.time).put("source", entry.video.sourceUrl)
                .put("title", entry.video.title).put("author", entry.video.author)
                .put("prefix", entry.video.prefixId).put("cover", entry.video.coverUrl).put("video", entry.video.videoUrl))
        }
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.path + ".tmp")
            temporary.writeText(JSONObject().put("scope", scope).put("entries", values).toString())
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // Optional cache: a full disk must not turn a successful online parse into a failure.
            // No media URL or service response is logged.
        }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        if (file == null || !file.isFile || file.length() > 512 * 1024) return
        try {
            val saved = JSONObject(file.readText())
            if (saved.optString("scope") != scope) return
            val values = saved.getJSONArray("entries")
            for (index in 0 until minOf(values.length(), 16)) {
                val entry = values.optJSONObject(index) ?: continue
                val time = entry.optLong("time")
                if (now() - time !in 0 until TTL) continue
                val source = ShareLink.extract(entry.optString("source")) ?: continue
                val video = VideoInfo(source, entry.optString("title"), entry.optString("author"),
                    entry.optString("cover"), entry.optString("video"), prefixId = entry.optString("prefix"))
                if (video.downloadable) entries[source] = Entry(time, video)
            }
        } catch (_: IOException) {
            // Cache miss; the network resolver still works.
        } catch (_: org.json.JSONException) {
            // A partial/old cache never blocks the application.
        }
    }

    companion object { private const val TTL = 5 * 60_000L }
}

