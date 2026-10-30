package app.streamsave.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.streamsave.ui.liquid.LiquidBottomTab
import app.streamsave.ui.liquid.LiquidBottomTabs
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** Actual shader lens and magnification, using the original implementation credited by Miuix. */
@Composable
fun GlassNavigation(selected: Int, select: (Int) -> Unit, dark: Boolean, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(selected)
    val selectedIndex = remember { { current } }
    val foreground = if (dark) Color(0xFFEEEEEF) else Color(0xFF25272B)
    LiquidBottomTabs(selectedIndex, select, backdrop, tabsCount = 3, dark = dark,
        modifier = modifier.padding(horizontal = 34.dp).widthIn(max = 360.dp).fillMaxWidth().testTag("liquid_navigation")) {
        listOf("首页" to Icons.Outlined.Home, "历史" to Icons.Outlined.History, "设置" to Icons.Outlined.Settings).forEachIndexed { index, item ->
            LiquidBottomTab(onClick = { select(index) }, selected = selected == index) {
                Icon(item.second, contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
                Text(item.first, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
