package io.github.vferries.encarte

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppContainerTest {
    private val app = ApplicationProvider.getApplicationContext<EncarteApp>()

    @Test
    fun theStartupCleanupEmptiesTheImportFolderButKeepsAFileBeingHandedOver() {
        val leftover = File(app.cacheDir, "imports/${UUID.randomUUID()}").apply {
            parentFile!!.mkdirs()
            writeText("left by a killed process")
        }
        val handedOver = app.container.importFiles.copy { "just copied by ImportActivity".byteInputStream() }

        runBlocking { app.container.cleanUpLeftovers() }

        assertFalse(leftover.exists())
        assertTrue(handedOver.exists())
    }
}
