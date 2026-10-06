package io.github.vferries.encarte.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchRequestsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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
}
