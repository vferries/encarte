package io.github.vferries.encarte.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExpiryStatusTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun noDateIsNone() = assertEquals(ExpiryStatus.None, expiryStatus(null, today))

    @Test
    fun yesterdayIsExpired() = assertEquals(ExpiryStatus.Expired, expiryStatus(today.minusDays(1), today))

    @Test
    fun todayIsStillValid() = assertEquals(ExpiryStatus.Soon(0), expiryStatus(today, today))

    @Test
    fun thirtyDaysIsSoon() = assertEquals(ExpiryStatus.Soon(30), expiryStatus(today.plusDays(30), today))

    @Test
    fun thirtyOneDaysIsLater() = assertEquals(ExpiryStatus.Later, expiryStatus(today.plusDays(31), today))
}
