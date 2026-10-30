package app.streamsave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.streamsave.data.DownloadStatus
import app.streamsave.data.HistoryEntry
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MiuixTheme.colorScheme.surface)
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier = Modifier.fillMaxWidth().shadow(if (enabled) 12.dp else 0.dp, RoundedCornerShape(50), ambientColor = Color(0x220066CC), spotColor = Color(0x330066CC)), enabled = enabled, cornerRadius = 50.dp,
        minHeight = 56.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier = modifier.shadow(if (enabled) 3.dp else 0.dp, RoundedCornerShape(50), ambientColor = Color(0x12000000), spotColor = Color(0x18000000)), enabled = enabled, cornerRadius = 50.dp, minHeight = 48.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp)) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp, lineHeight = 22.sp)
}
fun sizeText(bytes: Long): String = when {
    bytes < 0 -> "大小未知"
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.CHINA, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024))
    else -> "${bytes / 1024} KB"
}
fun statusText(row: HistoryEntry) = when (row.status) {
    DownloadStatus.QUEUED -> "等待网络 · 准备下载"
    DownloadStatus.DOWNLOADING -> if (row.total > 0) "下载中 · ${row.bytes * 100 / row.total}%" else "下载中 · ${sizeText(row.bytes)}"
    DownloadStatus.SAVING -> "正在保存到相册"
    DownloadStatus.COMPLETE -> "已保存到相册"
    DownloadStatus.PAUSED -> "下载已暂停"
    DownloadStatus.FAILED -> "下载未完成"
    DownloadStatus.CANCELLED -> "已取消"
}
@Composable
fun TransferProgress(row: HistoryEntry, cancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(statusText(row), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (row.total > 0) Caption("${sizeText(row.bytes)} / ${sizeText(row.total)}")
        }
        if (row.active) {
            if (row.total > 0 && row.status == DownloadStatus.DOWNLOADING) LinearProgressIndicator(progress = (row.bytes.toFloat() / row.total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SecondaryAction("取消下载", cancel)
        }
        if (row.error.isNotBlank()) Caption(row.error)
    }
}
