package app.streamsave.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.streamsave.StreamSaveApp
import app.streamsave.data.HistoryEntry
import app.streamsave.parser.ShareLink
import app.streamsave.parser.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(val text: String = "", val loading: Boolean = false, val video: VideoInfo? = null, val error: String = "", val busy: Boolean = false, val loginRequired: Boolean = false)

class MainViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val graph = (application as StreamSaveApp).graph
    private val mutable = MutableStateFlow(HomeState(text = saved["link"] ?: ""))
    val home = mutable.asStateFlow()
    val history = graph.history.entries
    val settings = graph.preferences.state
    val resolverState = graph.resolver.state
    private var availabilityCheck: Job? = null
    val page = saved.getStateFlow("page", 0)
    private var parsing: Job? = null
    private var generation = 0
    private var lastClipboard = ""
    init {
        viewModelScope.launch {
            try { graph.scheduler.reconcile() }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "无法读取下载历史，请检查手机存储空间") }
        }
        if (mutable.value.text.isNotBlank()) parse()
    }
    fun checkWebsite(force: Boolean = false) {
        if (availabilityCheck?.isActive == true) return
        availabilityCheck = viewModelScope.launch { graph.resolver.checkWebsite(force) }
    }
    fun selectResolver(provider: app.streamsave.parser.ResolverProvider) {
        if (provider == graph.preferences.state.value.resolver) return
        parsing?.cancel(); generation++
        graph.preferences.resolver(provider)
        graph.resolver.select(provider)
        mutable.value = mutable.value.copy(loading = false, video = null, error = "", loginRequired = false)
        if (ShareLink.extract(mutable.value.text) != null) parse()
    }
    fun navigate(index: Int) { saved["page"] = index }
    fun loginReturned() {
        parsing?.cancel(); generation++
        mutable.value = mutable.value.copy(loading = false, loginRequired = false)
        if (ShareLink.extract(mutable.value.text) != null) { navigate(0); parse() }
    }
    fun edit(text: String) {
        parsing?.cancel()
        generation++
        val limited = text.take(16384)
        saved["link"] = limited
        mutable.value = HomeState(text = limited)
        if (ShareLink.extract(limited) != null) {
            parsing = viewModelScope.launch { delay(500); parse() }
        }
    }
    fun receive(text: String, clipboard: Boolean = false) {
        val link = ShareLink.extract(text)
        if (clipboard) {
            if (link == null || lastClipboard == link) return
            lastClipboard = link
            if (link == ShareLink.extract(mutable.value.text)) return
        }
        if (link == null) { mutable.value = mutable.value.copy(error = "分享内容中没有视频号链接"); return }
        if (link == ShareLink.extract(mutable.value.text) && (mutable.value.loading || mutable.value.video != null)) { navigate(0); return }
        navigate(0)
        parsing?.cancel()
        saved["link"] = link
        mutable.value = HomeState(text = link)
        parse()
    }
    fun parse() {
        if (mutable.value.loading) return
        val link = ShareLink.extract(mutable.value.text)
        if (link == null) { mutable.value = mutable.value.copy(error = "请粘贴 weixin.qq.com/sph/ 开头的视频号链接"); return }
        parsing?.cancel()
        val version = ++generation
        mutable.value = mutable.value.copy(loading = true, error = "", video = null, loginRequired = false)
        parsing = viewModelScope.launch {
            try {
                val video = graph.resolver.resolve(link)
                if (version == generation) mutable.value = mutable.value.copy(loading = false, video = video)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (version == generation) mutable.value = mutable.value.copy(loading = false, loginRequired = e is app.streamsave.parser.TencentLoginRequired, error = when (e) {
                    is java.net.UnknownHostException -> "网络不可用，请检查网络后重试"
                    is java.net.SocketTimeoutException -> "解析超时，请稍后重试"
                    is java.io.IOException -> e.message ?: "网络请求失败"
                    else -> "解析失败（${e.javaClass.simpleName}），请稍后重试"
                })
            }
        }
    }
    fun download() {
        val state = mutable.value
        val video = state.video ?: return
        if (!video.downloadable || state.busy) return
        mutable.value = state.copy(busy = true, error = "")
        viewModelScope.launch {
            try { graph.scheduler.enqueue(video) }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "无法启动下载") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun retry(row: HistoryEntry) { viewModelScope.launch {
        try { graph.scheduler.resume(row.id) }
        catch (_: Exception) { mutable.value = mutable.value.copy(error = "无法恢复下载，请返回首页重新解析") }
    } }
    fun cancel(row: HistoryEntry) { viewModelScope.launch { graph.scheduler.cancel(row.id) } }
    suspend fun delete(row: HistoryEntry, file: Boolean) {
        if (row.active) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (file) graph.library.delete(row.uri)
            graph.engine.partial(row.id).delete()
            graph.history.delete(row.id)
        }
    }
}
