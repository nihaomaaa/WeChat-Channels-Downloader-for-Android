package app.streamsave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.streamsave.data.HistoryEntry
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(state: HomeState, transfer: HistoryEntry?, edit: (String) -> Unit, paste: () -> Unit, parse: () -> Unit, download: () -> Unit, cancel: (HistoryEntry) -> Unit, sessionSaved: Boolean, login: () -> Unit, resolver: app.streamsave.parser.ResolverState) {
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(top = 32.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("微信视频号下载", fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Caption("喜欢的视频，留在相册。")
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Link, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                Text("视频号链接", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            BasicTextField(state.text, edit, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)
                .clip(RoundedCornerShape(17.dp)).background(MiuixTheme.colorScheme.background).padding(16.dp),
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary), maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { parse() }), decorationBox = { inner ->
                    Box { if (state.text.isEmpty()) Caption("粘贴视频号分享链接\nhttps://weixin.qq.com/sph/…"); inner() }
                })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = paste, modifier = Modifier.weight(1f), minHeight = 48.dp, cornerRadius = 50.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text("粘贴链接", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                SecondaryAction(if (state.loading) "正在解析…" else if (state.video != null) "重新解析" else "解析链接", parse, Modifier.weight(1f), enabled = !state.loading)
            }
        }
        Caption(resolverStatus(resolver))
        if (state.loading) Panel {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在获取视频信息", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Caption("正在读取视频信息与下载地址")
        }
        if (state.error.isNotBlank()) Panel {
            Text("暂时未能完成", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Caption(state.error)
            SecondaryAction("重试解析", parse)
            if (state.loginRequired) SecondaryAction("登录腾讯元宝", login)
            
        }
        state.video?.let { video ->
            Panel {
                if (video.coverUrl.isNotBlank()) AsyncImage(video.coverUrl, "视频封面", modifier = Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (video.author.isNotBlank()) Text(video.author, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(video.title, fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    if (video.downloadable) Caption("MP4 视频  ·  保存至系统相册")
                }
                if (video.notice.isNotBlank()) Caption(video.notice)
                if (video.needsLogin) PrimaryAction("登录后获取下载地址", onClick = login)
                
                if (transfer?.active == true) {
                    TransferProgress(transfer) { cancel(transfer) }
                } else {
                    if (transfer != null) TransferProgress(transfer) { cancel(transfer) }
                    if (video.downloadable) PrimaryAction(if (state.busy) "正在准备…" else if (transfer?.status == app.streamsave.data.DownloadStatus.COMPLETE) "再次下载" else "下载到相册", !state.busy, download)
                }
            }
        }
        if (state.video == null && !state.loading && state.error.isBlank()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(34.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .45f))
                Caption("复制链接后回到这里，或从微信分享链接到本应用。")
                if (resolver.effective == app.streamsave.parser.ResolverProvider.YUANBAO && !sessionSaved) {
                    Caption("元宝方式需要在腾讯官方页面登录一次")
                    SecondaryAction("登录腾讯元宝", login)
                }
            }
        }
    }
}

