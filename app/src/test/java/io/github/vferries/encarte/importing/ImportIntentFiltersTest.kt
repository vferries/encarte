package io.github.vferries.encarte.importing

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What other apps can hand to Encarté, as Android resolves it from the manifest. */
@RunWith(AndroidJUnit4::class)
class ImportIntentFiltersTest {
    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()
    private val attachment = Uri.parse("content://com.example.mail.provider/attachments/42")

    private fun opensInEncarte(intent: Intent): Boolean = app.packageManager
        .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        .any { it.activityInfo.name == ImportActivity::class.java.name }

    private fun view(type: String, uri: Uri = attachment) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)

    private fun send(type: String) = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, attachment)

    @Test
    fun aPassResolvesForOpenWithAndShare() {
        for (type in listOf("application/vnd.apple.pkpass", "application/vnd-com.apple.pkpass")) {
            assertTrue("VIEW $type", opensInEncarte(view(type)))
            assertTrue("SEND $type", opensInEncarte(send(type)))
        }
    }

    @Test
    fun aPdfResolvesForShareOnly() {
        assertTrue(opensInEncarte(send("application/pdf")))
        assertFalse(opensInEncarte(view("application/pdf")))
    }

    @Test
    fun nothingElseResolves() {
        assertFalse("file: URIs", opensInEncarte(view("application/vnd.apple.pkpass", Uri.parse("file:///sdcard/Download/a.pkpass"))))
        assertFalse(opensInEncarte(send("application/octet-stream")))
        assertFalse(opensInEncarte(send("image/png")))
        assertFalse(opensInEncarte(send("text/plain")))
    }
}
