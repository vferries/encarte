package io.github.vferries.encarte.importing

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.EncarteApp
import io.github.vferries.encarte.MainActivity
import io.github.vferries.encarte.navigation.LaunchRequests
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ImportActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

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
    fun aProviderFailureIsRefused() {
        val failures = listOf<() -> Nothing>(
            { throw IllegalArgumentException("bad") },
            { throw IllegalStateException("bad") },
            { throw UnsupportedOperationException("bad") },
            { throw NullPointerException("bad") },
        )
        for (failure in failures) {
            serve(attachment) { failure() }
            runToTheEnd(view(attachment))
            assertRefused()
        }
    }

    @Test
    fun aProviderStreamFailingWhileReadingIsRefused() {
        serve(attachment) {
            object : InputStream() {
                override fun read(): Int = throw NullPointerException("bad")
                override fun read(b: ByteArray, off: Int, len: Int): Int = throw NullPointerException("bad")
            }
        }

        runToTheEnd(view(attachment))

        assertRefused()
    }

    @Test
    fun aFileUriIsRefusedEvenWhenItIsAValidPass() {
        val own = File(app.filesDir, "own.pkpass").apply { writeBytes(TestFiles.pass()) }

        runToTheEnd(view(Uri.fromFile(own)))

        assertRefused()
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun anotherActionIsRefusedEvenForAServedPass() {
        serve(attachment) { TestFiles.pass().inputStream() }

        runToTheEnd(view(attachment).setAction(Intent.ACTION_EDIT))

        assertRefused()
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun aCopyFinishingAfterTheActivityWasLeftIsDeleted() {
        val reading = CountDownLatch(1)
        val gate = CountDownLatch(1)
        serve(attachment) {
            object : InputStream() {
                override fun read(): Int = -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reading.countDown()
                    gate.await(5, TimeUnit.SECONDS)
                    return -1
                }
            }
        }

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.DESTROYED)
            gate.countDown()
            val deadline = System.currentTimeMillis() + 5_000
            while (copies().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertEquals(emptyList<File>(), copies())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    /** A provider that never answers until [gate] opens, then streams forever: only a cancelled copy stops reading. */
    private fun serveSlow(gate: CountDownLatch, reads: AtomicInteger, reading: CountDownLatch = CountDownLatch(1)) =
        serve(attachment) {
            object : InputStream() {
                override fun read(): Int = 'x'.code
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reads.incrementAndGet()
                    reading.countDown()
                    gate.await(5, TimeUnit.SECONDS)
                    b[off] = 'x'.code.toByte()
                    return 1
                }
            }
        }

    /** The delay runs on the main looper, which Robolectric holds still until told how much time passes. */
    private fun pass(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)

    /** The IO side posts its result to the paused main looper, which holds it until the test idles it. */
    private fun awaitTheCopyResultOnTheMainLooper() {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 5_000
        while (looper.isIdle && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertFalse("the copy's result never reached the main looper", looper.isIdle)
    }

    private fun awaitNoCopy() {
        val deadline = System.currentTimeMillis() + 5_000
        while (copies().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
    }

    @Test
    fun theProgressCardAppearsOnlyWhenTheCopyTakesMoreThanFourHundredMilliseconds() {
        val gate = CountDownLatch(1)
        serveSlow(gate, AtomicInteger())

        ActivityScenario.launch<ImportActivity>(view(attachment)).use {
            pass(300)
            composeRule.onAllNodesWithText("Importing…").assertCountEquals(0)

            pass(200)
            composeRule.onNodeWithText("Importing…").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").assertIsDisplayed()
            gate.countDown()
        }
    }

    @Test
    fun cancellingStopsTheCopyDeletesItAndStartsNothing() {
        val gate = CountDownLatch(1)
        val reading = CountDownLatch(1)
        val reads = AtomicInteger()
        serveSlow(gate, reads, reading)

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            pass(500)
            composeRule.onNodeWithText("Cancel").performClick()
            assertTrue(scenario.state == Lifecycle.State.DESTROYED || finishing(scenario))
            gate.countDown()
            awaitNoCopy()
            Thread.sleep(100)
            assertEquals("the copy stopped reading", 1, reads.get())
        }

        assertEquals(emptyList<File>(), copies())
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun backCancelsTheCopyLikeCancel() {
        val gate = CountDownLatch(1)
        val reading = CountDownLatch(1)
        serveSlow(gate, AtomicInteger(), reading)

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertTrue(scenario.state == Lifecycle.State.DESTROYED || finishing(scenario))
            gate.countDown()
            awaitNoCopy()
        }

        assertEquals(emptyList<File>(), copies())
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun aCopyEndingBeforeTheDelayNeverShowsTheCardEvenWhenTimePassesAfterwards() {
        val gate = CountDownLatch(1)
        val closed = CountDownLatch(1)
        serve(attachment) {
            object : InputStream() {
                override fun read(): Int = -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    gate.await(5, TimeUnit.SECONDS)
                    return -1
                }
                override fun close() = closed.countDown()
            }
        }

        ActivityScenario.launch<ImportActivity>(view(attachment)).use {
            pass(300)
            gate.countDown()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            awaitTheCopyResultOnTheMainLooper()
            pass(500)

            composeRule.onAllNodesWithText("Importing…").assertCountEquals(0)
        }
        assertEquals(LaunchRequests.ACTION_IMPORT_FILE, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun cancellingAfterTheCopyEndedButBeforeTheHandOverLeavesNoFile() {
        val gate = CountDownLatch(1)
        val closed = CountDownLatch(1)
        serve(attachment) {
            object : InputStream() {
                override fun read(): Int = -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    gate.await(5, TimeUnit.SECONDS)
                    return -1
                }
                override fun close() = closed.countDown()
            }
        }

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            var activity: ImportActivity? = null
            scenario.onActivity { activity = it }
            gate.countDown()
            assertTrue(closed.await(5, TimeUnit.SECONDS))
            awaitTheCopyResultOnTheMainLooper()
            activity!!.onBackPressedDispatcher.onBackPressed() // before the looper runs the hand-over
            shadowOf(Looper.getMainLooper()).idle()
            awaitNoCopy()
        }

        assertEquals(emptyList<File>(), copies())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun backBeforeTheCardShowsKeepsItFromShowingLater() {
        val gate = CountDownLatch(1)
        serveSlow(gate, AtomicInteger())

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            pass(300)
            // finish() leaves the activity RESUMED here, so only the cancelled copy can keep the card away.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            pass(200)

            composeRule.onAllNodesWithText("Importing…").assertCountEquals(0)
            gate.countDown()
        }
    }

    @Test
    fun backAfterTheHandOverIsNotACancellation() {
        val pass = TestFiles.pass()
        val gate = CountDownLatch(1)
        // A copy ending before withContext suspends hands over inside onCreate, and launch then returns a destroyed
        // activity: Back needs the copy to end once the activity is up.
        serve(attachment) {
            object : FilterInputStream(pass.inputStream()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    gate.await(5, TimeUnit.SECONDS)
                    return super.read(b, off, len)
                }
            }
        }

        ActivityScenario.launch<ImportActivity>(view(attachment)).use { scenario ->
            gate.countDown()
            val deadline = System.currentTimeMillis() + 5_000
            while (shadowOf(app).peekNextStartedActivity() == null) {
                shadowOf(Looper.getMainLooper()).idle()
                check(System.currentTimeMillis() < deadline) { "ImportActivity never handed the file over" }
                Thread.sleep(10)
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }

        assertEquals(emptyList<String>(), ShadowLog.getLogsForTag("ImportActivity").map { it.msg }.filter { "cancelled" in it })
        assertEquals(1, copies().size)
    }

    @Test
    fun theProgressCardTextIsAPoliteLiveRegion() {
        val gate = CountDownLatch(1)
        serveSlow(gate, AtomicInteger())

        ActivityScenario.launch<ImportActivity>(view(attachment)).use {
            pass(500)
            composeRule.onNode(hasText("Importing…"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            gate.countDown()
        }
    }

    private fun finishing(scenario: ActivityScenario<ImportActivity>): Boolean {
        var finishing = false
        scenario.onActivity { finishing = it.isFinishing }
        return finishing
    }
}
