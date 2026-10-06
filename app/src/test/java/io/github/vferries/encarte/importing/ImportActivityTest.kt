package io.github.vferries.encarte.importing

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.MainActivity
import io.github.vferries.encarte.navigation.LaunchRequests
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class ImportActivityTest {
    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()
    private val attachment = Uri.parse("content://com.example.mail.provider/attachments/42")
    private val importDir = File(app.cacheDir, "imports")

    private fun view(uri: Uri?) = Intent(Intent.ACTION_VIEW)
        .setClass(app, ImportActivity::class.java)
        .setDataAndType(uri, "application/vnd.apple.pkpass")

    private fun send(uri: Uri?) = Intent(Intent.ACTION_SEND)
        .setClass(app, ImportActivity::class.java)
        .setType("application/pdf")
        .apply { if (uri != null) putExtra(Intent.EXTRA_STREAM, uri) }

    private fun serve(uri: Uri, content: () -> InputStream) =
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri, content)

    /** Runs the activity until it finishes; the copy happens on Dispatchers.IO, then resumes on the main looper. */
    private fun runToTheEnd(intent: Intent) {
        ActivityScenario.launch<ImportActivity>(intent).use { scenario ->
            val deadline = System.currentTimeMillis() + 5_000
            while (true) {
                shadowOf(Looper.getMainLooper()).idle()
                // Finishing inside onCreate reaches DESTROYED, and onActivity then has no activity to give.
                if (scenario.state == Lifecycle.State.DESTROYED) return
                var finishing = false
                scenario.onActivity { finishing = it.isFinishing }
                if (finishing) return
                check(System.currentTimeMillis() < deadline) { "ImportActivity never finished" }
                Thread.sleep(10)
            }
        }
    }

    private fun copies() = importDir.listFiles().orEmpty().toList()

    private fun assertRefused() {
        assertEquals("Can't open this file.", ShadowToast.getTextOfLatestToast())
        assertNull("nothing is opened", shadowOf(app).nextStartedActivity)
        assertEquals(emptyList<File>(), copies())
    }

    @Test
    fun anOpenedPassIsCopiedAndHandedToMainActivity() {
        val pass = TestFiles.pass()
        serve(attachment) { pass.inputStream() }

        runToTheEnd(view(attachment))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(ComponentName(app, MainActivity::class.java), started.component)
        assertEquals(LaunchRequests.ACTION_IMPORT_FILE, started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        val copy = File(importDir, started.getStringExtra(LaunchRequests.EXTRA_FILE_NAME)!!)
        assertArrayEquals(pass, copy.readBytes())
    }

    @Test
    fun aSharedPdfIsCopiedToo() {
        serve(attachment) { TestFiles.pdf.inputStream() }

        runToTheEnd(send(attachment))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(LaunchRequests.ACTION_IMPORT_FILE, started.action)
        assertEquals(1, copies().size)
    }

    @Test
    fun aFileOverTwentyMegabytesIsRefused() {
        serve(attachment) { ByteArray((MAX_IMPORT_BYTES + 1).toInt()).inputStream() }

        runToTheEnd(view(attachment))

        assertRefused()
    }

    @Test
    fun anUnreadableFileIsRefused() {
        serve(attachment) { throw SecurityException("grant revoked") }

        runToTheEnd(view(attachment))

        assertRefused()
    }

    @Test
    fun aRequestWithoutAFileIsRefused() {
        runToTheEnd(view(null))
        assertRefused()

        runToTheEnd(send(null))
        assertRefused()
    }

    @Test
    fun aFileUriOrAnotherActionIsRefused() {
        runToTheEnd(view(Uri.fromFile(File(app.filesDir, "../databases/encarte.db"))))
        assertRefused()

        runToTheEnd(view(attachment).setAction(Intent.ACTION_EDIT))
        assertRefused()
    }
}
