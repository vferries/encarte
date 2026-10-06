package io.github.vferries.encarte.launcher

import android.app.Application
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vferries.encarte.R
import io.github.vferries.encarte.navigation.LaunchRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.xmlpull.v1.XmlPullParser

// A plain Application: EncarteApp's LauncherSync would publish to the same ShortcutManager mid-test.
@Config(application = Application::class)
@RunWith(AndroidJUnit4::class)
class ShortcutPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val publisher = AndroidShortcutPublisher(context)
    private val fnac = LauncherCard(1, "Fnac", 0xFF1976D2.toInt())
    private val zara = LauncherCard(2, "Zara", 0xFFD32F2F.toInt())
    private val ikea = LauncherCard(3, "Ikea", 0xFFFBC02D.toInt())
    private val lidl = LauncherCard(4, "Lidl", 0xFF388E3C.toInt())

    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    private fun pin(cardId: Long, label: String) {
        val info = ShortcutInfoCompat.Builder(context, cardShortcutId(cardId))
            .setShortLabel(label)
            .setIntent(LaunchRequests.viewCard(context, cardId))
            .build()
        shortcutManager.requestPinShortcut(info.toShortcutInfo(), null)
    }

    /** On Android a pinned menu entry stays dynamic too; Robolectric's requestPinShortcut would take it out of the menu. */
    private fun pinMenuEntry(id: String) {
        val info = shortcutManager.dynamicShortcuts.single { it.id == id }
        val pinned = ReflectionHelpers.getStaticField<Int>(ShortcutInfo::class.java, "FLAG_PINNED")
        ReflectionHelpers.callInstanceMethod<Unit>(info, "addFlags", ClassParameter.from(Int::class.javaPrimitiveType, pinned))
    }

    // One sync per test: Robolectric's shadow drops the pinned flag of a shortcut it updates.
    private fun pinnedLabel(id: String) = shortcutManager.pinnedShortcuts.single { it.id == id }.shortLabel.toString()

    @Test
    fun publishReplacesTheCardShortcutsInOrder() {
        assertTrue(publisher.publish(listOf(zara, fnac)))

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(listOf("card:2", "card:1"), shortcuts.map { it.id })
        assertEquals("Zara", shortcuts.first().shortLabel.toString())
        val intent = shortcuts.first().intent
        assertEquals(LaunchRequests.ACTION_VIEW_CARD, intent.action)
        assertEquals(2L, intent.getLongExtra(LaunchRequests.EXTRA_CARD_ID, 0))

        publisher.publish(emptyList())
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun theCardLimitLeavesRoomForAddCard() {
        shadowOf(shortcutManager).setMaxShortcutCountPerActivity(5)
        assertEquals(3, publisher.cardLimit)

        shadowOf(shortcutManager).setMaxShortcutCountPerActivity(3)
        assertEquals(2, publisher.cardLimit)
    }

    @Test
    fun aPinnedShortcutHidesItsStoreWhileLocked() {
        pin(fnac.id, "Fnac")

        publisher.syncPinned(listOf(fnac), locked = true)

        assertEquals("Encarté", pinnedLabel("card:1"))
    }

    @Test
    fun anUnlockedPinnedShortcutShowsTheCurrentStoreName() {
        pin(fnac.id, "Encarté")

        publisher.syncPinned(listOf(fnac), locked = false)

        assertEquals("Fnac", pinnedLabel("card:1"))
    }

    @Test
    fun anUnlockedSyncKeepsTheMenuOrder() {
        publisher.publish(listOf(fnac, zara, ikea))
        pinMenuEntry("card:2")
        pin(lidl.id, "Encarté")

        publisher.syncPinned(listOf(fnac, zara, ikea, lidl), locked = false)

        val ranks = ShortcutManagerCompat.getDynamicShortcuts(context).associate { it.id to it.rank }
        assertEquals(mapOf("card:1" to 0, "card:2" to 1, "card:3" to 2), ranks)
        assertEquals("Lidl", pinnedLabel("card:4"))
    }

    @Test
    fun pinnedShortcutsOfDeletedCardsAreDisabledAndOthersUpdated() {
        val changes = pinnedChanges(listOf("card:1", "card:2", "add_card"), listOf(fnac))

        assertEquals(listOf("card:2"), changes.disable)
        assertEquals(listOf(fnac), changes.update)
    }

    @Test
    fun theStaticShortcutAddsACard() {
        val parser = context.resources.getXml(R.xml.shortcuts)
        var action: String? = null
        var targetClass: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                action = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "action")
                targetClass = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetClass")
            }
        }

        assertEquals(LaunchRequests.ACTION_ADD_CARD, action)
        assertEquals("io.github.vferries.encarte.MainActivity", targetClass)
    }
}
