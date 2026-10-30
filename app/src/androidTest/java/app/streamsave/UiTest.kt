package app.streamsave

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun startTabsThemeAndActivityRecreation() {
        compose.onNodeWithText("微信视频号下载", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("历史").performClick()
        compose.onNodeWithText("下载历史").assertExists()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("深色").performClick()
        val pixels = compose.onNodeWithText("外观").captureToImage().toPixelMap()
        assertTrue("Dark mode heading must contain bright readable text", (0 until pixels.width).any { x ->
            (0 until pixels.height).any { y -> pixels[x, y].luminance() > 0.6f }
        })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("外观").assertExists()
        compose.onNodeWithText("浅色").performClick()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("视频号链接").assertExists()
    }
    @Test fun invalidLinkSurvivesRotationWithoutCrash() {
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("not a video link")
        compose.onNodeWithText("解析链接").performScrollTo().performClick()
        compose.onNodeWithText("暂时未能完成").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("not a video link").assertExists()
    }
    @Test fun liquidLensDragsAcrossTabs() {
        val nav = compose.onNodeWithTag("liquid_navigation")
        nav.performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width / 6f, centerY),
                androidx.compose.ui.geometry.Offset(width * 5f / 6f, centerY), 700)
        }
        compose.onNodeWithText("外观").assertExists()
        compose.onNodeWithText("配置元宝凭证").assertDoesNotExist()
        compose.onNodeWithText("首页").performClick()
        compose.onNodeWithText("视频号链接").assertExists()
    }
    @Test fun creditsShowAuthorAndRealProjectsAcrossRecreation() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("作者：nihaomaaa · GitHub").performScrollTo().assertExists()
        compose.onNodeWithText("开源项目与参考").performScrollTo().performClick()
        compose.onNodeWithText("Miuix").assertExists()
        compose.onNodeWithText("github.com/compose-miuix-ui/miuix").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("credits_list").performScrollToNode(hasText("wx_channels_download"))
        compose.onNodeWithText("wx_channels_download").assertExists()
        compose.onNodeWithTag("credits_list").performScrollToIndex(0)
        compose.onNodeWithText("返回设置").performClick()
        compose.onNodeWithText("外观").assertExists()
    }
    @Test fun settingsKeepBothResolversAndRestoreChoice() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("sph.miuistore.com").performScrollTo().assertExists()
        compose.onNodeWithText("腾讯元宝").performScrollTo().performClick()
        compose.onNodeWithText("当前使用：腾讯元宝").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("当前使用：腾讯元宝").performScrollTo().assertExists()
        compose.onNodeWithText("登录腾讯元宝").performScrollTo().assertExists()
        compose.onNodeWithText("下载网站").performScrollTo().performClick()
    }
}
