package app.streamsave

import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.streamsave.data.TencentSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TencentSessionTest {
    @Test fun appCookieRequiresExplicitEnableAndClearInvalidatesVerification(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<StreamSaveApp>()
        val session = app.graph.tencentSession
        session.clear()
        val ready = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().setCookie(TencentSession.ORIGIN,
                "TEST_ONLY_LOCAL=not_a_real_login; Secure; HttpOnly; Path=/") { ready.countDown() }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        try {
            assertNull(session.credentials())
            InstrumentationRegistry.getInstrumentation().runOnMainSync { session.enable("FixtureAgent") }
            assertFalse("Cookies alone must not claim authentication", session.state.value.verified)
            val credentials = session.credentials()!!
            assertTrue(credentials.cookie.contains("TEST_ONLY_LOCAL="))
            assertEquals("FixtureAgent", credentials.userAgent)
            session.accepted(credentials.revision)
            assertTrue(session.state.value.verified)
            session.rejected(credentials.revision)
            assertTrue(session.state.value.expired)
            session.clear()
            session.accepted(credentials.revision)
            assertFalse(session.state.value.enabled)
            assertFalse(session.state.value.verified)
            assertNull(session.credentials())
        } finally { session.clear() }
    }
}
