package app.streamsave.ui

import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun TencentSessionPanel(model: MainViewModel, login: () -> Unit) {
    val session by model.graph.tencentSession.state.collectAsState()
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Panel {
        Text("腾讯网页登录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Caption(when {
            session.expired -> "腾讯未接受当前会话，请重新登录"
            session.verified -> "当前会话曾成功解析视频；如失效，重新登录即可"
            session.enabled -> "已保留网页会话，解析视频时确认是否有效"
            else -> "选用元宝或网站不可用时，在腾讯官方页面登录一次。无需复制 Cookie 或配置服务器。"
        })
        SecondaryAction(if (session.enabled) "重新打开登录页" else "登录腾讯元宝", login, enabled = !clearing)
        SecondaryAction(if (clearing) "正在清除…" else "清除本机网页登录", {
            clearing = true
            scope.launch {
                try { model.graph.tencentSession.clear(); message = "已清除本应用的网页登录信息" }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { message = "未能清除网页登录，请更新系统网页组件后重试" }
                finally { clearing = false }
            }
        }, enabled = !clearing)
        if (message.isNotBlank()) Caption(message)
    }
}
