package app.streamsave.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

private data class Credit(val name: String, val repository: String, val role: String)
private val runtimeCredits = listOf(
    Credit("Miuix", "compose-miuix-ui/miuix", "界面组件与主题 · Apache-2.0"),
    Credit("AndroidLiquidGlass / Backdrop", "Kyant0/AndroidLiquidGlass", "液态玻璃渲染与改编导航组件 · Apache-2.0"),
    Credit("Shapes", "Kyant0/Shapes", "平滑形状 · Apache-2.0"),
    Credit("AndroidX / Jetpack Compose", "androidx/androidx", "Android 组件、Compose 与 Material 图标 · Apache-2.0"),
    Credit("Kotlin", "JetBrains/kotlin", "语言与标准库 · Apache-2.0"),
    Credit("kotlinx.coroutines", "Kotlin/kotlinx.coroutines", "协程 · Apache-2.0"),
    Credit("OkHttp", "square/okhttp", "网络传输；测试使用 MockWebServer · Apache-2.0"),
    Credit("Okio", "square/okio", "流式 I/O · Apache-2.0"),
    Credit("Coil", "coil-kt/coil", "图片加载 · Apache-2.0"),
    Credit("desugar_jdk_libs", "google/desugar_jdk_libs", "Java API 兼容 · Apache-2.0 / GPLv2 + Classpath Exception"),
)
private val toolingCredits = listOf(
    Credit("Gradle", "gradle/gradle", "构建与 Wrapper · Apache-2.0"),
    Credit("AndroidX Test", "android/android-test", "设备测试 · Apache-2.0"),
    Credit("JUnit 4", "junit-team/junit4", "测试 · EPL-1.0"),
    Credit("JSON-java", "stleary/JSON-java", "JVM 测试 · Public domain"),
    Credit("Node.js", "nodejs/node", "1.2 归档服务运行环境；1.3 手机端不依赖 · MIT 等"),
    Credit("Caddy", "caddyserver/caddy", "1.2 归档服务 HTTPS；1.3 手机端不依赖 · Apache-2.0"),
    Credit("Cloudflare Workers SDK", "cloudflare/workers-sdk", "1.2 归档部署工具；1.3 手机端不依赖 · Apache-2.0 / MIT"),
)
private val referenceCredits = listOf(
    Credit("wx_channels_download", "ltaoo/wx_channels_download", "视频号协议研究参考；未复制其服务器或解密器 · MIT + Commons Clause"),
    Credit("InstallerX-Revived", "wxxsfxyzm/InstallerX-Revived", "视觉与交互参考；未复制其代码 · GPL-3.0"),
    Credit("jsoup（1.1 历史依赖）", "jhy/jsoup", "旧版 HTML 解析；1.2 已移除 · MIT"),
)

@Composable
fun CreditsScreen(back: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = back)
    LazyColumn(Modifier.fillMaxSize().testTag("credits_list").statusBarsPadding().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 26.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SecondaryAction("返回设置", back) }
        item { Text("开源项目与参考", fontSize = 27.sp, fontWeight = FontWeight.Bold) }
        item { Caption("感谢以下项目及其维护者。点击项目查看 GitHub。各项目保留原作者与许可证；本应用作者标注不改变上游版权。") }
        listOf("应用组件" to runtimeCredits, "构建、测试与历史工具" to toolingCredits, "研究与设计参考" to referenceCredits).forEach { (heading, entries) ->
            item { Text(heading, Modifier.padding(top = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            items(entries, key = { it.repository }) { item ->
                Panel(Modifier.clickable {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${item.repository}"))) }
                    catch (_: ActivityNotFoundException) { Toast.makeText(context, "没有可打开链接的浏览器", Toast.LENGTH_SHORT).show() }
                    catch (_: SecurityException) { Toast.makeText(context, "系统暂不允许打开此链接", Toast.LENGTH_SHORT).show() }
                }) {
                    Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Caption(item.role)
                    Caption("github.com/${item.repository}")
                }
            }
        }
        item { Caption("完整许可证随 APK 和源码一并提供。应用并非腾讯或微信官方产品。") }
    }
}

