package com.example.adobongkangkong.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Instant.toPrettyTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    pattern: String = "h:mm a"
): String {
    val zdt = atZone(zoneId)

    val datePart = when (zdt.toLocalDate()) {
        LocalDate.now(zoneId) -> "Today"
        LocalDate.now(zoneId).minusDays(1) -> "Yesterday"
        else -> zdt.format(DateTimeFormatter.ofPattern("MMM d"))
    }

    val timePart = zdt.format(DateTimeFormatter.ofPattern(pattern))
    return "$datePart · $timePart"
}