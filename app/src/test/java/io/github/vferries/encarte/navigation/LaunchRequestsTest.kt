package io.github.vferries.encarte.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class LaunchRequestsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun warnings() = ShadowLog.getLogsForTag("LaunchRequests").filter { it.type == android.util.Log.WARN }

    @Before
    fun clearLogs() = ShadowLog.clear()

    @Test
    fun viewCardOpensTheCardAboveTheList() {
        assertEquals(listOf(CardListKey, CardDisplayKey(42)), LaunchRequests.backStackFor(LaunchRequests.viewCard(context, 42)))
    }

    @Test
    fun addCardOpensTheScannerAboveTheList() {
        assertEquals(listOf(CardListKey, ScannerKey()), LaunchRequests.backStackFor(LaunchRequests.addCard(context)))
    }

    @Test
    fun anInvalidCardIdOpensTheListOnly() {
        for (id in listOf(0L, -3L)) {
            assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(LaunchRequests.viewCard(context, id)))
        }
        assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(Intent(LaunchRequests.ACTION_VIEW_CARD)))
    }

    @Test
    fun anyOtherLaunchOpensTheList() {
        assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(null))
        assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun requestsLaunchLikeAFreshStart() {
        val intent = LaunchRequests.viewCard(context, 1)

        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    @Test
    fun anUnknownActionOpensTheListAndIsLogged() {
        assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(Intent("com.example.OTHER")))

        assertEquals(1, warnings().size)
        assertTrue(warnings().single().msg.contains("com.example.OTHER"))
    }

    @Test
    fun aWrongTypedCardIdOpensTheListAndIsLogged() {
        val intent = LaunchRequests.launch(context, LaunchRequests.ACTION_VIEW_CARD).putExtra(LaunchRequests.EXTRA_CARD_ID, "abc")

        assertEquals(listOf(CardListKey), LaunchRequests.backStackFor(intent))
        assertEquals(1, warnings().size)
    }

    @Test
    fun aNormalLaunchLogsNothing() {
        LaunchRequests.backStackFor(Intent(Intent.ACTION_MAIN))
        LaunchRequests.backStackFor(Intent())
        LaunchRequests.backStackFor(null)

        assertTrue(warnings().isEmpty())
    }
}
