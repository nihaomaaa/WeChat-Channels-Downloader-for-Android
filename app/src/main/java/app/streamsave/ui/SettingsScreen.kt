// finesse · design-language=apple · apple-mode=app · grouped settings
package app.streamsave.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.streamsave.BuildConfig
import app.streamsave.data.Settings
import app.streamsave.parser.ResolverProvider
import top.yukonga.miuix.kmp.basic.*

@Composable
fun SettingsScreen(settings: Settings, model: MainViewModel, login: () -> Unit) {
    var credits by rememberSaveable { mutableStateOf(false) }
    if (credits) { CreditsScreen { credits = false }; return }
    val resolverState by model.resolverState.collectAsState()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(top = 32.dp, bottom = 148.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("设置", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Panel {
            Text("外观", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                    Button(onClick = { model.graph.preferences.theme(index) }, modifier = Modifier.weight(1f), cornerRadius = 50.dp,
                        insideMargin = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                        colors = if (settings.theme == index) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("自动识别剪贴板", fontSize = 16.sp)
                    Caption("回到应用时识别视频号链接")
                }
                Switch(checked = settings.clipboard, onCheckedChange = model.graph.preferences::clipboard)
            }
        }
        Panel {
            Text("解析方式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("下载网站" to ResolverProvider.WEBSITE, "腾讯元宝" to ResolverProvider.YUANBAO).forEach { (label, provider) ->
                    Button(onClick = { model.selectResolver(provider) }, modifier = Modifier.weight(1f), cornerRadius = 50.dp, minHeight = 48.dp,
                        colors = if (settings.resolver == provider) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Caption(resolverStatus(resolverState))
            if (resolverState.detail.isNotBlank()) Caption(resolverState.detail)
            Caption("网站打不开时临时使用元宝；恢复后按你的选择解析。元宝需要在腾讯官方页面登录。")
            SecondaryAction("重新检测网站", { model.checkWebsite(force = true) }, enabled = !resolverState.checking)
        }
        TencentSessionPanel(model, login)
        Panel {
            Text("解析与保存", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("sph.miuistore.com", fontSize = 16.sp)
            Caption("网站方式无需登录；使用该方式时分享链接会发给网站。元宝方式直接连接腾讯，不向网站发送链接或腾讯会话。网站可用性及限制由其运营方决定。")
            SecondaryAction("打开解析网站", {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sph.miuistore.com/"))) }
                catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "未安装浏览器", Toast.LENGTH_SHORT).show() }
                catch (_: SecurityException) { Toast.makeText(context, "系统暂不允许打开链接", Toast.LENGTH_SHORT).show() }
            })
            Text("保存位置", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Caption("系统相册 · Movies/微信视频号下载\n无需访问你的全部照片")
        }
        Panel {
            Text("微信视频号下载", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Caption("版本 " + BuildConfig.VERSION_NAME)
            SecondaryAction("作者：nihaomaaa · GitHub", {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nihaomaaa"))) }
                catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "未安装浏览器", Toast.LENGTH_SHORT).show() }
                catch (_: SecurityException) { Toast.makeText(context, "系统暂不允许打开链接", Toast.LENGTH_SHORT).show() }
            })
            SecondaryAction("开源项目与参考", { credits = true })
        }
        Caption("下载历史保存在本机。本应用不上传本机视频，也不向解析网站发送腾讯登录记录。")
    }
}
