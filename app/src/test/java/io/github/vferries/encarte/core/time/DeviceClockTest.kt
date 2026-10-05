package io.github.vferries.encarte.core.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class DeviceClockTest {
    private val original = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(original)

    @Test
    fun zoneFollowsTheDefaultZoneChangedAfterCreation() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val clock = DeviceClock()
        assertEquals(ZoneId.of("Pacific/Kiritimati"), clock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago"))

        assertEquals(ZoneId.of("Pacific/Pago_Pago"), clock.zone)
    }

    @Test
    fun todayFollowsTheDefaultZone() {
        val clock = DeviceClock()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
        val east = LocalDate.now(clock)
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago")) // UTC-11
        val west = LocalDate.now(clock)

        // 25 hours apart: the calendar dates always differ by one or two days.
        assertEquals(true, east.isAfter(west))
    }

    @Test
    fun withZonePinsTheGivenZoneAndInstantIsNow() {
        val pinned = DeviceClock().withZone(ZoneId.of("UTC"))
        assertEquals(ZoneId.of("UTC"), pinned.zone)
        val before = Instant.now()
        val now = DeviceClock().instant()
        assertEquals(true, !now.isBefore(before))
    }
}
