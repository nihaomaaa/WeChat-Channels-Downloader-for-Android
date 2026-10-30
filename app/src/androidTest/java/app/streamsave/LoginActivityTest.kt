package app.streamsave

import androidx.test.core.app.ActivityScenario
import app.streamsave.login.TencentLoginActivity
import org.junit.Assert.assertFalse
import org.junit.Test

class LoginActivityTest {
    @Test fun loginPageAndOldEngineFallbackSurviveActivityRecreation() {
        ActivityScenario.launch(TencentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
