package app.streamsave.ui

import app.streamsave.parser.ResolverProvider
import app.streamsave.parser.ResolverState
import app.streamsave.parser.WebsiteHealth

fun resolverStatus(state: ResolverState): String = when {
    state.preferred == ResolverProvider.YUANBAO -> "当前使用：腾讯元宝"
    state.checking -> "正在检测下载网站…"
    state.fallback -> "网站暂不可用，已临时切换到腾讯元宝"
    state.health == WebsiteHealth.AVAILABLE -> "当前使用：下载网站"
    else -> "等待检测下载网站"
}
