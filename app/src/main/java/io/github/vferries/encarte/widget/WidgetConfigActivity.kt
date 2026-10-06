package io.github.vferries.encarte.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.vferries.encarte.AppContainer
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import io.github.vferries.encarte.launcher.WidgetSource
import io.github.vferries.encarte.lock.AppLockGate
import io.github.vferries.encarte.lock.DeviceAuthenticator
import kotlinx.coroutines.launch
import java.io.IOException

private const val TAG = "WidgetConfigActivity"

/**
 * Chooses what a widget shows. It lists group names, so it sits behind the app lock like MainActivity.
 * FragmentActivity: BiometricPrompt requires it.
 */
class WidgetConfigActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Any exit without a choice cancels: the launcher then drops a widget being placed.
        setResult(RESULT_CANCELED, resultFor(appWidgetId))
        if (!isOurWidget(appWidgetId)) {
            Log.w(TAG, "Widget $appWidgetId is not an Encarté widget")
            finish()
            return
        }
        enableEdgeToEdge()
        val container = (application as EncarteApp).container
        val authenticator = DeviceAuthenticator(this)
        setContent {
            EncarteTheme {
                // Started for a result inside the launcher's task: Back must cancel, not send that task back.
                AppLockGate(
                    container.lockManager,
                    container.settingsRepository,
                    authenticator::authenticate,
                    onBackWhileCovered = ::finish,
                ) {
                    val groupsFlow = remember { container.groupRepository.observeGroups(cardCollator()) }
                    val groups by groupsFlow.collectAsStateWithLifecycle(emptyList())
                    WidgetConfigScreen(groups, onChoose = { choose(container, appWidgetId, it) }, onClose = ::finish)
                }
            }
        }
    }

    // The activity is exported for the launcher: any app could start it with another app's widget id.
    private fun isOurWidget(appWidgetId: Int): Boolean =
        appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            AppWidgetManager.getInstance(this)
                ?.getAppWidgetIds(ComponentName(this, CardsWidgetProvider::class.java))
                ?.contains(appWidgetId) == true

    private fun choose(container: AppContainer, appWidgetId: Int, source: WidgetSource) {
        lifecycleScope.launch {
            try {
                container.widgetSources.set(appWidgetId, source)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot save the source of widget $appWidgetId", e)
                finish()
                return@launch
            }
            // The launcher shows the widget as soon as the result comes back: draw it first.
            container.launcherSync.renderWidgets(intArrayOf(appWidgetId))
            setResult(RESULT_OK, resultFor(appWidgetId))
            finish()
        }
    }

    private fun resultFor(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
