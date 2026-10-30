/* finesse · register=h5 · morph=A-app-shell · design-language=apple · apple-mode=app
 * A=neutral+blue · B=system-type · C=single-column · D=press-feedback · E=functional-glass */
package app.streamsave.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

/** Three overlapping, alpha-masked blur radii. Only the screen edges are sampled.
 * API 29/30 use an opaque gradient fallback; the center remains untouched. */
@Composable
fun ProgressiveEdgeBlur(backdrop: Backdrop, top: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(3f, 9f, 18f).forEachIndexed { index, radius ->
                Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val start = index * .19f
                        val stops = if (top) arrayOf(0f to Color.Black, (1f-start)*.5f to Color.Black,
                            (1f-start) to Color.Transparent, 1f to Color.Transparent)
                        else arrayOf(0f to Color.Transparent, start to Color.Transparent,
                            (start+1f)*.5f to Color.Black, 1f to Color.Black)
                        drawRect(Brush.verticalGradient(*stops), blendMode = BlendMode.DstIn)
                    }
                    .drawPlainBackdrop(backdrop = backdrop, shape = { RectangleShape },
                        effects = { blur(radius.dp.toPx()) }))
            }
        }
        val strength = if (Build.VERSION.SDK_INT >= 31) .72f else .98f
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            if (top) listOf(color.copy(alpha = strength), color.copy(alpha = .15f), Color.Transparent)
            else listOf(Color.Transparent, color.copy(alpha = .15f), color.copy(alpha = strength)))))
    }
}
