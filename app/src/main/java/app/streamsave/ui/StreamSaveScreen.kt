/* finesse · register=h5 · design-language=apple · apple-mode=app
 * A=neutral+blue · B=system-type · C=single-column · D=press-feedback · E=functional-glass */
package app.streamsave.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun StreamSaveScreen(model: MainViewModel, paste: (Boolean) -> Unit, download: () -> Unit, login: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val home by model.home.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val page by model.page.collectAsStateWithLifecycle()
    val tencentSession by model.graph.tencentSession.state.collectAsStateWithLifecycle()
    val resolverState by model.resolverState.collectAsStateWithLifecycle()
    val dark = settings.theme == 2 || (settings.theme == 0 && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    val palette = if (dark) darkColorScheme(background = Color(0xFF000000), surface = Color(0xFF1C1C1E), primary = Color(0xFF70B8FF), onPrimary = Color(0xFF14294C))
        else lightColorScheme(background = Color(0xFFF2F2F7), surface = Color.White, primary = Color(0xFF0066CC))
    MiuixTheme(colors = palette) {
      CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onBackground) {
        val backdrop = rememberLayerBackdrop()
        BackHandler(page != 0) { model.navigate(0) }
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.background)) {
                when (page) {
                    0 -> HomeScreen(home, history.firstOrNull { it.video.sourceUrl == home.video?.sourceUrl }, model::edit,
                        { paste(false) }, model::parse, download, { row -> model.cancel(row) }, tencentSession.enabled, login, resolverState)
                    1 -> HistoryScreen(history, model)
                    else -> SettingsScreen(settings, model, login)
                }
            }
            ProgressiveEdgeBlur(backdrop, true, palette.background, Modifier.align(Alignment.TopCenter)
                .fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 32.dp))
            ProgressiveEdgeBlur(backdrop, false, palette.background, Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 116.dp))
            GlassNavigation(page, model::navigate, dark, backdrop,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 12.dp))
        }
      }
    }
}
