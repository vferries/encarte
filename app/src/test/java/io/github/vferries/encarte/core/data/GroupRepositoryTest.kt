package io.github.vferries.encarte.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.cards.list.cardCollator
import io.github.vferries.encarte.testing.inMemoryDatabase
import io.github.vferries.encarte.testing.testCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {
    private val db = inMemoryDatabase()
    private val groups = GroupRepository(db)
    private val collator = cardCollator(Locale.FRANCE)

    @After
    fun tearDown() = db.close()

    private suspend fun created(name: String): Long = (groups.create(name) as GroupNameResult.Saved).id

    private suspend fun names() = groups.observeGroups(collator).first().map { it.name }

    @Test
    fun namesAreTrimmedAndSortedLikeCards() = runTest {
        created(" Mode ")
        created("électronique")
        created("Courses")

        assertEquals(listOf("Courses", "électronique", "Mode"), names())
    }

    @Test
    fun blankNamesAreRefused() = runTest {
        assertEquals(GroupNameResult.Blank, groups.create("   "))
        val id = created("Courses")

        assertEquals(GroupNameResult.Blank, groups.rename(id, ""))
        assertEquals(listOf("Courses"), names())
    }

    @Test
    fun namesClashIgnoringCaseAndAccents() = runTest {
        created("Courses")

        assertEquals(GroupNameResult.Duplicate, groups.create("coursès "))
        assertEquals(GroupNameResult.Duplicate, groups.create("COURSES"))
        assertEquals(listOf("Courses"), names())
    }

    @Test
    fun emojiNamesDoNotClash() = runTest {
        created("🛒")

        assertTrue(groups.create("👕") is GroupNameResult.Saved)
    }

    @Test
    fun renameMayChangeTheCaseOfItsOwnName() = runTest {
        val id = created("courses")

        assertEquals(GroupNameResult.Saved(id), groups.rename(id, "Courses"))
        assertEquals(listOf("Courses"), names())
    }

    @Test
    fun renameToAnotherGroupsNameIsRefused() = runTest {
        created("Courses")
        val mode = created("Mode")

        assertEquals(GroupNameResult.Duplicate, groups.rename(mode, "courses"))
    }

    @Test
    fun membershipsAreAddedAndRemoved() = runTest {
        val fnac = db.cardDao().insert(testCard("Fnac"))
        val courses = created("Courses")
        val mode = created("Mode")

        groups.setMembership(fnac, courses, member = true)
        groups.setMembership(fnac, mode, member = true)
        groups.setMembership(fnac, courses, member = true) // already a member: no error
        assertEquals(mapOf(fnac to setOf(courses, mode)), groups.observeMemberships().first())

        groups.setMembership(fnac, courses, member = false)
        assertEquals(setOf(mode), groups.groupIdsOf(fnac))
    }

    @Test
    fun deletingAGroupKeepsItsCards() = runTest {
        val fnac = db.cardDao().insert(testCard("Fnac"))
        val courses = created("Courses")
        groups.setMembership(fnac, courses, member = true)

        groups.delete(courses)

        assertEquals(listOf("Fnac"), db.cardDao().getAll().map { it.storeName })
        assertEquals(emptyMap<Long, Set<Long>>(), groups.observeMemberships().first())
    }

    @Test
    fun deletingACardRemovesItsMemberships() = runTest {
        val fnac = db.cardDao().insert(testCard("Fnac"))
        val courses = created("Courses")
        groups.setMembership(fnac, courses, member = true)

        db.cardDao().deleteById(fnac)

        assertEquals(emptySet<Long>(), groups.groupIdsOf(fnac))
        assertEquals(listOf("Courses"), names())
    }
}
