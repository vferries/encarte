package io.github.vferries.encarte.widget

import android.app.Activity
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.core.data.GroupNameResult
import io.github.vferries.encarte.launcher.WidgetSource
import io.github.vferries.encarte.lock.LockState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetConfigActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()

    private fun configure(appWidgetId: Int) = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setClass(app, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun boundWidget(appWidgetId: Int = 42): Int {
        shadowOf(AppWidgetManager.getInstance(app)).bindAppWidgetId(appWidgetId, ComponentName(app, CardsWidgetProvider::class.java))
        return appWidgetId
    }

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    // Robolectric only reaches DESTROYED once scenario.result waits for it: isFinishing is the observable signal.
    private fun waitUntilFinished(scenario: ActivityScenario<*>) = composeRule.waitUntil(5_000) {
        var finishing = false
        scenario.onActivity { finishing = it.isFinishing }
        finishing
    }

    /** The lock follows the process: lock it as after more than a minute in the background. */
    private fun lockTheApp() {
        runBlocking { app.container.settingsRepository.setLockEnabled(true) }
        val lock = app.container.lockManager
        // The manager reads the setting on the main thread, once DataStore delivers it.
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            lock.onBackground()
            ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
            lock.onForeground()
            if (lock.state.value == LockState.LOCKED) return
            Thread.sleep(20)
        }
        fail("The app never locked")
    }

    @Test
    fun aWidgetThatIsNotOursIsRefused() {
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configure(99))

        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun choosingAGroupSavesItAndConfirmsTheWidget() {
        val courses = runBlocking { (app.container.groupRepository.create("Courses") as GroupNameResult.Saved).id }
        val appWidgetId = boundWidget()
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configure(appWidgetId))
        waitForText("Courses")
        composeRule.onNodeWithText("Show in the widget").assertIsDisplayed()
        composeRule.onNodeWithText("★ Favorites").assertIsDisplayed()

        composeRule.onNodeWithText("Courses").performClick()
        waitUntilFinished(scenario)

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(appWidgetId, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        assertEquals(WidgetSource.Group(courses), runBlocking { app.container.widgetSources.sources.first()[appWidgetId] })
    }

    @Test
    fun backCancelsTheWidget() {
        val scenario = ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configure(boundWidget()))
        waitForText("Show in the widget")

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitUntilFinished(scenario)

        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun theChoiceStaysBehindTheAppLock() {
        runBlocking { app.container.groupRepository.create("Courses") }
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        lockTheApp()

        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(configure(boundWidget())).use {
            waitForText("Encarté is locked")
            composeRule.onNodeWithText("Courses").assertDoesNotExist()
        }
    }
}
