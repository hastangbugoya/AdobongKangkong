package com.example.adobongkangkong.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class TimeRange(
    val startInclusive: Instant,
    val endExclusive: Instant
)

fun todayRange(zoneId: ZoneId = ZoneId.systemDefault()): TimeRange {
    val now = ZonedDateTime.now(zoneId)
    val start = now.toLocalDate().atStartOfDay(zoneId).toInstant()
    val end = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
    return TimeRange(start, end)
}
