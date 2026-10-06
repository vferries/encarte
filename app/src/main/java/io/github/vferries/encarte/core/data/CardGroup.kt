package io.github.vferries.encarte.core.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.text.Normalizer
import java.util.Locale

@Entity(tableName = "groups")
data class CardGroup(
    // AUTOINCREMENT: a deleted group's id is never reused, so a stale reference (a widget, a saved selection)
    // can't silently point at another group.
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored trimmed; unique by [groupNameKey], which GroupRepository enforces. */
    val name: String,
)

@Entity(
    tableName = "card_groups",
    primaryKeys = ["cardId", "groupId"],
    foreignKeys = [
        ForeignKey(entity = Card::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CardGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    // cardId is covered by the primary key's index.
    indices = [Index("groupId")],
)
data class CardGroupCrossRef(val cardId: Long, val groupId: Long)

private val combiningMarks = Regex("\\p{Mn}+")

/**
 * Case and accents fold, but punctuation and symbols stay: unlike normalizedForMatching(), which would turn
 * every emoji-only name into the same empty key, "🛒" and "👕" remain two groups.
 */
fun groupNameKey(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replace(combiningMarks, "").lowercase(Locale.ROOT)
