package io.github.vferries.encarte.launcher

import android.content.Context
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
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ShortcutPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val publisher = AndroidShortcutPublisher(context)
    private val fnac = LauncherCard(1, "Fnac", 0xFF1976D2.toInt())
    private val zara = LauncherCard(2, "Zara", 0xFFD32F2F.toInt())

    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    private fun pin(cardId: Long, label: String) {
        val info = ShortcutInfoCompat.Builder(context, cardShortcutId(cardId))
            .setShortLabel(label)
            .setIntent(LaunchRequests.viewCard(context, cardId))
            .build()
        shortcutManager.requestPinShortcut(info.toShortcutInfo(), null)
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
