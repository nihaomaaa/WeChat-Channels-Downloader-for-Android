package app.streamsave.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.streamsave.data.DownloadStatus
import app.streamsave.data.HistoryEntry
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(rows: List<HistoryEntry>, model: MainViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var deletion by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    fun open(row: HistoryEntry, share: Boolean) {
        scope.launch {
            if (!withContext(Dispatchers.IO) { model.graph.library.exists(row.uri) }) {
                message("视频已被移走或删除，可以重新下载"); return@launch
            }
            val uri = Uri.parse(row.uri)
            val intent = if (share) Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("视频", uri)
            try { context.startActivity(if (share) Intent.createChooser(intent, "分享视频") else intent) }
            catch (_: android.content.ActivityNotFoundException) { message("未找到支持此操作的应用") }
            catch (_: SecurityException) { message("无法访问此视频，请重新下载") }
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("下载历史", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Caption(if (rows.isEmpty()) "保存过的美好，都在这里" else "${rows.count { it.status == DownloadStatus.COMPLETE }} 个视频已保存")
            }
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 110.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Outlined.VideoLibrary, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .5f), modifier = Modifier.size(48.dp))
                Text("还没有下载记录", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Caption("从首页粘贴第一条视频号链接")
                SecondaryAction("去首页", { model.navigate(0) })
            }
        }
        items(rows, key = { it.id }) { row ->
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AsyncImage(if (row.status == DownloadStatus.COMPLETE) row.video.coverUrl else row.video.coverUrl, null,
                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.background), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.video.title, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Caption(SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(row.created)) + " · " + sizeText(row.bytes))
                    }
                }
                TransferProgress(row) { model.cancel(row) }
                if (!row.active) {
                    if (row.status == DownloadStatus.COMPLETE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction("播放", { open(row, false) }, Modifier.weight(1f))
                        SecondaryAction("分享", { open(row, true) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction(if (row.status == DownloadStatus.COMPLETE) "重新下载" else "继续 / 重试", {
                            if (row.status == DownloadStatus.COMPLETE) model.receive(row.video.sourceUrl) else model.retry(row)
                        }, Modifier.weight(1f))
                        SecondaryAction("删除", { deletion = row }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    deletion?.let { row ->
        Dialog(onDismissRequest = { if (!deleting) deletion = null }) {
            Panel {
                Text("删除这条记录？", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Caption("仅移除记录会保留系统相册中的视频。删除视频后将无法从本机恢复。")
                fun remove(file: Boolean) {
                    if (deleting) return
                    deleting = true
                    scope.launch {
                        try { model.delete(row, file); deletion = null }
                        catch (_: Exception) { message("删除未完成，系统可能已变更文件访问权限") }
                        finally { deleting = false }
                    }
                }
                PrimaryAction("仅移除记录", !deleting) { remove(false) }
                if (row.uri.isNotBlank()) SecondaryAction("删除视频与记录", { remove(true) }, Modifier.fillMaxWidth())
                SecondaryAction("取消", { if (!deleting) deletion = null }, Modifier.fillMaxWidth())
            }
        }
    }
}
