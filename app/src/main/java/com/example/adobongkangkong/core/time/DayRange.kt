package com.example.adobongkangkong.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class InstantRange(val startInclusive: Instant, val endExclusive: Instant)

fun dayRange(date: LocalDate, zoneId: ZoneId): InstantRange {
    val start = date.atStartOfDay(zoneId).toInstant()
    val end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    return InstantRange(startInclusive = start, endExclusive = end)
}
