package app.streamsave.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.streamsave.parser.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DownloadStatus { QUEUED, DOWNLOADING, SAVING, COMPLETE, PAUSED, FAILED, CANCELLED }
data class HistoryEntry(
    val id: Long = 0,
    val video: VideoInfo,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytes: Long = 0,
    val total: Long = -1,
    val uri: String = "",
    val etag: String = "",
    val error: String = "",
    val created: Long = System.currentTimeMillis(),
) {
    val active get() = status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.SAVING)
    val jobId get() = (id + 1000).toInt()
}

/** SQLite is the durable source of truth; mutations and emission are serialized off the UI thread. */
class HistoryStore(context: Context) : SQLiteOpenHelper(context, "history.db", null, 2) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries = mutable.asStateFlow()
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE downloads (
            id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, title TEXT NOT NULL,
            author TEXT NOT NULL, cover TEXT NOT NULL, media TEXT NOT NULL, status TEXT NOT NULL,
            bytes INTEGER NOT NULL, total INTEGER NOT NULL, uri TEXT NOT NULL, etag TEXT NOT NULL,
            error TEXT NOT NULL, created INTEGER NOT NULL, prefix TEXT NOT NULL DEFAULT '')""")
        db.execSQL("CREATE INDEX downloads_source ON downloads(source)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE downloads ADD COLUMN prefix TEXT NOT NULL DEFAULT ''")
    }
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { emit() } }
    suspend fun get(id: Long): HistoryEntry? = withContext(Dispatchers.IO) { mutex.withLock { getRow(id) } }
    suspend fun enqueue(video: VideoInfo): HistoryEntry = withContext(Dispatchers.IO) { mutex.withLock {
        val current = list().firstOrNull { it.active && it.video.sourceUrl == video.sourceUrl }
        if (current != null) return@withLock current
        val row = HistoryEntry(video = video)
        val id = writableDatabase.insertOrThrow("downloads", null, row.values())
        emit()
        row.copy(id = id)
    } }
    suspend fun update(id: Long, block: (HistoryEntry) -> HistoryEntry): HistoryEntry? = withContext(Dispatchers.IO) { mutex.withLock {
        val previous = getRow(id) ?: return@withLock null
        val next = block(previous)
        writableDatabase.update("downloads", next.values(), "id=?", arrayOf(id.toString()))
        emit()
        next
    } }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { mutex.withLock {
        writableDatabase.delete("downloads", "id=?", arrayOf(id.toString())); emit()
    } }
    private fun emit() { mutable.value = list() }
    private fun list(): List<HistoryEntry> = readableDatabase.rawQuery("SELECT * FROM downloads ORDER BY id DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(c.row()) }
    }
    private fun getRow(id: Long) = readableDatabase.rawQuery("SELECT * FROM downloads WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.row() else null }
    private fun Cursor.row(): HistoryEntry {
        fun s(name: String) = getString(getColumnIndexOrThrow(name))
        fun n(name: String) = getLong(getColumnIndexOrThrow(name))
        return HistoryEntry(n("id"), VideoInfo(s("source"), s("title"), s("author"), s("cover"), s("media"), prefixId = s("prefix")),
            DownloadStatus.valueOf(s("status")), n("bytes"), n("total"), s("uri"), s("etag"), s("error"), n("created"))
    }
    private fun HistoryEntry.values() = ContentValues().apply {
        put("source", video.sourceUrl); put("title", video.title); put("author", video.author)
        put("prefix", video.prefixId); put("cover", video.coverUrl); put("media", video.videoUrl); put("status", status.name)
        put("bytes", bytes); put("total", total); put("uri", uri); put("etag", etag); put("error", error); put("created", created)
    }
}
