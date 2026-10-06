package io.github.vferries.encarte.importing

import io.github.vferries.encarte.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ImportViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun theFileIsReadOnceAndItsOutcomePublished() = runTest {
        val read = CompletableDeferred<ImportOutcome>()
        val names = mutableListOf<String>()
        val vm = ImportViewModel("3f2c1e7a-0000-4000-8000-000000000001") { name ->
            names += name
            read.await()
        }
        assertNull("still reading", vm.outcome.value)

        val outcome = ImportOutcome.Failure(ImportFailure.FILE_GONE)
        read.complete(outcome)

        assertEquals(outcome, vm.outcome.first { it != null })
        assertEquals(listOf("3f2c1e7a-0000-4000-8000-000000000001"), names)
    }
}
