package io.github.vferries.encarte.core.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Cards expiring within this many days get a "Soon" badge. */
const val EXPIRY_SOON_DAYS = 30

sealed interface ExpiryStatus {
    /** No expiry date. */
    data object None : ExpiryStatus

    /** Expires more than [EXPIRY_SOON_DAYS] days from today: no badge. */
    data object Later : ExpiryStatus

    /** 0 means today, the card's last valid day. */
    data class Soon(val daysLeft: Int) : ExpiryStatus

    data object Expired : ExpiryStatus
}

fun expiryStatus(expiresOn: LocalDate?, today: LocalDate): ExpiryStatus {
    if (expiresOn == null) return ExpiryStatus.None
    val daysLeft = ChronoUnit.DAYS.between(today, expiresOn)
    return when {
        daysLeft < 0 -> ExpiryStatus.Expired
        daysLeft <= EXPIRY_SOON_DAYS -> ExpiryStatus.Soon(daysLeft.toInt())
        else -> ExpiryStatus.Later
    }
}
