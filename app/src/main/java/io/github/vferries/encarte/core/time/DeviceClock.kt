package io.github.vferries.encarte.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Wall clock in the device's current time zone. Unlike [Clock.systemDefaultZone], which freezes the zone when
 * created, this reads it on every call: Android updates the default zone of running processes when the user travels.
 */
class DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
