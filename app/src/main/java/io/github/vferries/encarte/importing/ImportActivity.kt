package io.github.vferries.encarte.importing

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.R
import io.github.vferries.encarte.navigation.LaunchRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "ImportActivity"

/**
 * Receives a pass or a PDF from another app ("Open with", "Share"). It runs in that app's task, so it only copies
 * the file while the read grant lasts, hands the copy to MainActivity in Encarté's own task, and finishes. On
 * MainActivity, the filters would start a second Encarté inside the mail app's task. It shows and reads no card
 * data: the app lock applies in MainActivity.
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
        lifecycleScope.launch {
            val copy = copy(files, uri)
            if (copy == null) {
                refuse()
            } else {
                startActivity(LaunchRequests.importFile(this@ImportActivity, copy.name))
                finish()
            }
        }
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

    // The exceptions are logged without the URI or the message: another app's provider chose both.
    private suspend fun copy(files: ImportFiles, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            files.copy { contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No content") }
        } catch (e: IOException) {
            // Includes FileTooLargeException.
            Log.w(TAG, "Cannot copy the shared file: ${e.javaClass.simpleName}")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "The shared file is not readable")
            null
        } catch (e: IllegalArgumentException) {
            providerFailed(e)
        } catch (e: IllegalStateException) {
            providerFailed(e)
        } catch (e: UnsupportedOperationException) {
            providerFailed(e)
        }
    }

    private fun providerFailed(e: RuntimeException): File? {
        Log.w(TAG, "The provider failed: ${e.javaClass.simpleName}")
        return null
    }

    private fun refuse() {
        Toast.makeText(applicationContext, R.string.import_file_cannot_open, Toast.LENGTH_LONG).show()
        finish()
    }
}
