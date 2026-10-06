package io.github.vferries.encarte.importing

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.R
import io.github.vferries.encarte.core.ui.theme.EncarteTheme
import io.github.vferries.encarte.navigation.LaunchRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ImportActivity"

/** A quick import must not flash a card: it shows only when the copy is visibly slow. */
private const val CARD_DELAY_MS = 400L

/**
 * Receives a pass or a PDF from another app ("Open with", "Share"). It runs in that app's task, so it only copies
 * the file while the read grant lasts, hands the copy to MainActivity in Encarté's own task, and finishes. On
 * MainActivity, the filters would start a second Encarté inside the mail app's task.
 *
 * The window stays transparent unless the copy takes a while: then a card with Cancel shows, or the screen would
 * look frozen under an invisible window. It shows and reads no card data: the app lock applies in MainActivity.
 */
class ImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = sharedUri(intent)
        if (uri == null) {
            refuse()
            return
        }
        val files = (application as EncarteApp).container.importFiles
        var cardShown by mutableStateOf(false)
        // Set once the copy is complete, cleared on the hand-over: a cancel in between must not orphan the file.
        val pending = AtomicReference<File?>(null)
        val copying = lifecycleScope.launch {
            // Inside the copy's coroutine, so the card cannot show once the copy is over.
            val card = launch {
                delay(CARD_DELAY_MS)
                cardShown = true
            }
            val copy = copy(files, uri, pending)
            card.cancel()
            if (copy == null) {
                refuse()
            } else {
                pending.set(null)
                startActivity(LaunchRequests.importFile(this@ImportActivity, copy.name))
                finish()
            }
        }
        copying.invokeOnCompletion { cause ->
            if (cause != null) pending.getAndSet(null)?.let(files::delete)
        }
        val cancel = {
            // Past the hand-over, Back or a tap during the exit is not a cancellation.
            if (copying.isActive) {
                Log.i(TAG, "Import cancelled by the user")
                cancelImport(copying)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })
        setContent {
            EncarteTheme {
                if (cardShown) ImportProgressCard(onCancel = cancel)
            }
        }
    }

    private fun cancelImport(copying: Job) {
        copying.cancel()
        finish()
    }

    /** The file to read, or null (logged) for anything this activity's intent filters do not let through. */
    private fun sharedUri(intent: Intent): Uri? {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> {
                Log.w(TAG, "Unexpected action ${intent.action}")
                return null
            }
        }
        return when {
            uri == null -> null.also { Log.w(TAG, "${intent.action} without a file") }
            // The activity is exported: a file: URI would let another app make Encarté read its own private files.
            uri.scheme != ContentResolver.SCHEME_CONTENT -> null.also { Log.w(TAG, "Refusing a ${uri.scheme} URI") }
            else -> uri
        }
    }

    private suspend fun copy(files: ImportFiles, uri: Uri, pending: AtomicReference<File?>): File? =
        withContext(Dispatchers.IO) {
            val copy = files.copyOrNull(shouldContinue = { isActive }) {
                contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No content")
            }
            if (copy != null && !isActive) {
                // The activity was left during the copy: nobody will hand this file over.
                files.delete(copy)
                return@withContext null
            }
            // withContext drops a result whose job was cancelled meanwhile: the completion handler then deletes it.
            pending.set(copy)
            copy
        }

    private fun refuse() {
        Toast.makeText(applicationContext, R.string.import_file_cannot_open, Toast.LENGTH_LONG).show()
        finish()
    }
}
