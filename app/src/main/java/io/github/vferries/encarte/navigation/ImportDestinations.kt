package io.github.vferries.encarte.navigation

import androidx.navigation3.runtime.NavKey
import io.github.vferries.encarte.importing.CardDraft
import io.github.vferries.encarte.importing.ImportOutcome
import io.github.vferries.encarte.scan.ScannedCode

/** The editor for a scanned code; a code Encarté cannot draw keeps its number, with a notice. */
fun ScannedCode.editKey(groupId: Long?): CardEditKey =
    CardEditKey(barcodeValue = value, barcodeFormat = format, unsupportedFormat = format == null, groupId = groupId)

/** The editor for a draft; like a scan, a number without a drawable format comes with the notice. */
fun CardDraft.editKey(groupId: Long?): CardEditKey =
    CardEditKey(draft = this, unsupportedFormat = cardNumber.isNotEmpty() && barcodeFormat == null, groupId = groupId)

/** The screen an import leads to; null when there is only a failure to tell. */
fun ImportOutcome.destination(groupId: Long?): NavKey? = when (this) {
    is ImportOutcome.Draft -> draft.editKey(groupId)
    is ImportOutcome.Choice -> ImportChoiceKey(codes, groupId)
    is ImportOutcome.Image -> code?.editKey(groupId)
    is ImportOutcome.Failure -> null
}
