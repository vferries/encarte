package io.github.vferries.encarte

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EncarteApp"

class EncarteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.lockManager.onForeground()

            override fun onStop(owner: LifecycleOwner) = container.lockManager.onBackground()
        })
        MainScope().launch {
            // Best-effort housekeeping: a failure must not crash the app at every launch.
            try {
                container.cleanUpLeftovers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Startup cleanup failed", e)
            }
        }
        container.launcherSync.start(container.appScope)
    }
}
