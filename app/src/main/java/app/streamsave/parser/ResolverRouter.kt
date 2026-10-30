package app.streamsave.parser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class ResolverProvider { WEBSITE, YUANBAO }
enum class WebsiteHealth { UNKNOWN, AVAILABLE, UNAVAILABLE }
data class WebsiteCheck(val available: Boolean, val detail: String)
data class ResolverState(
    val preferred: ResolverProvider,
    val health: WebsiteHealth = WebsiteHealth.UNKNOWN,
    val checking: Boolean = false,
    val detail: String = "",
) {
    val effective: ResolverProvider get() =
        if (preferred == ResolverProvider.YUANBAO || health == WebsiteHealth.UNAVAILABLE) ResolverProvider.YUANBAO
        else ResolverProvider.WEBSITE
    val fallback: Boolean get() = preferred == ResolverProvider.WEBSITE && health == WebsiteHealth.UNAVAILABLE
}

/** User preference is durable; outage failover is temporary. Existing downloads keep their URLs.
 * A page health check is NOT evidence that every share link can be resolved. */
class ResolverRouter(
    initial: ResolverProvider,
    private val website: VideoResolver,
    private val yuanbao: VideoResolver,
    private val probe: suspend () -> WebsiteCheck,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : VideoResolver {
    private val mutable = MutableStateFlow(ResolverState(initial))
    val state = mutable.asStateFlow()
    private val probeMutex = Mutex()
    private var checkedAt: Long? = null

    fun select(provider: ResolverProvider) { mutable.update { it.copy(preferred = provider) } }

    /** Called on app foreground entry and by parsing if health is unknown/stale.
     * Concurrent entry/parse checks are coalesced; rotation or login return within 60s reuses it. */
    suspend fun checkWebsite(force: Boolean = false) = probeMutex.withLock {
        val age = checkedAt?.let { clock() - it }
        if (!force && age != null && age in 0 until 60_000) return@withLock
        mutable.update { it.copy(checking = true) }
        try {
            val result = try { probe() }
            catch (e: CancellationException) { throw e }
            catch (_: IOException) { WebsiteCheck(false, "无法连接下载网站") }
            checkedAt = clock()
            mutable.update { it.copy(
                health = if (result.available) WebsiteHealth.AVAILABLE else WebsiteHealth.UNAVAILABLE,
                detail = result.detail, checking = false) }
        } finally { mutable.update { it.copy(checking = false) } }
    }

    override suspend fun resolve(source: String): VideoInfo {
        // An invalid link never triggers a service request.
        val link = ShareLink.extract(source) ?: throw ResolveException("没有找到有效的视频号分享链接")
        if (state.value.preferred == ResolverProvider.WEBSITE) checkWebsite()
        if (state.value.effective == ResolverProvider.YUANBAO) return yuanbao.resolve(link).withProvider("腾讯元宝")
        return try { website.resolve(link).withProvider("下载网站") }
        catch (e: IOException) {
            // A content/quota rejection is not a site outage. Recheck before switching.
            checkWebsite(force = true)
            if (state.value.effective == ResolverProvider.YUANBAO) yuanbao.resolve(link).withProvider("腾讯元宝")
            else throw e
        }
    }

    private fun VideoInfo.withProvider(label: String) = copy(
        notice = listOf("解析方式：" + label, notice).filter { it.isNotBlank() }.joinToString("\n"))
}
