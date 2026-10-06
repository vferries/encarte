package io.github.vferries.encarte.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

// Kept apart from PassStringsTest: under Robolectric a @Test(timeout) never fails a CPU-bound test.
@RunWith(AndroidJUnit4::class)
class PassStringsLogTest {
    @Test
    fun manyMalformedLinesLogOneWarning() {
        ShadowLog.clear()

        PassStrings.parse("\"a\" = \"1\";\n" + "broken\n".repeat(50_000))

        assertEquals(1, ShadowLog.getLogs().count { it.tag == "PassStrings" })
    }
}
