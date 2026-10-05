package io.github.vferries.encarte.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A dialog or popup is its own window, drawn above the lock screen: only the wrappers hide them. */
class CoverableWindowsGuardTest {
    @Test
    fun screensOpenWindowsOnlyThroughTheCoverableWrappers() {
        // Gradle runs unit tests from the module directory.
        val sources = File("src/main/java")
        assertTrue("sources not found from ${File("").absolutePath}", sources.isDirectory)

        val offenders = sources.walk()
            .filter { it.isFile && it.extension == "kt" && it.name != "CoverableWindows.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.path}:${index + 1}: ${line.trim()}".takeIf { DIRECT_WINDOW_CALL.containsMatchIn(line) }
                }
            }
            .toList()

        assertEquals("Use the Encarte* wrappers from CoverableWindows.kt", emptyList<String>(), offenders)
    }

    @Test
    fun patternCatchesDirectCallsButNotLookalikes() {
        listOf(
            "AlertDialog(", "BasicAlertDialog(", "Dialog(onDismissRequest = {})", "DropdownMenu (",
            "ExposedDropdownMenu(", "DatePickerDialog(", "ModalBottomSheet(", "Popup(", "Popup {",
        ).forEach { assertTrue(it, DIRECT_WINDOW_CALL.containsMatchIn(it)) }
        listOf(
            "EncarteAlertDialog(", "EncarteDialog(", "EncarteExposedDropdownMenu(", "DropdownMenuItem(",
            "ExposedDropdownMenuBox(", "DialogProperties(", "PopupProperties {",
            "private fun ExportPasswordDialog(",
            "import androidx.compose.material3.AlertDialog",
        ).forEach { assertFalse(it, DIRECT_WINDOW_CALL.containsMatchIn(it)) }
    }

    private companion object {
        val DIRECT_WINDOW_CALL = Regex(
            """\b(AlertDialog|BasicAlertDialog|Dialog|DropdownMenu|ExposedDropdownMenu|DatePickerDialog|ModalBottomSheet|Popup)\s*[({]"""
        )
    }
}
