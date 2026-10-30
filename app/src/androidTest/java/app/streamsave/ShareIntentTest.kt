package app.streamsave

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.streamsave.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Test

class ShareIntentTest {
    @Test fun malformedShareColdStartShowsActionableError() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "This text contains no supported video link")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val model = ViewModelProvider(activity)[MainViewModel::class.java]
                assertEquals("分享内容中没有视频号链接", model.home.value.error)
            }
        }
    }
}
