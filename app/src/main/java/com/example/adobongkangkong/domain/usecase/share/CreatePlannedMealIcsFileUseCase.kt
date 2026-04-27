package com.example.adobongkangkong.domain.usecase.share

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class CreatePlannedMealIcsFileUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Input(
        val date: LocalDate,
        val title: String,
        val notes: String? = null,
        val startLocalTime: LocalTime,
        val durationMinutes: Long = 60,
        val timeZone: ZoneId = ZoneId.systemDefault()
    )

    operator fun invoke(input: Input): File {
        val uid = UUID.randomUUID().toString()
        val now = Instant.now()

        val start = ZonedDateTime.of(input.date, input.startLocalTime, input.timeZone)
        val end = start.plusMinutes(input.durationMinutes)

        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//AdobongKangkong//CalendarInvite//EN")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:${formatUtc(now)}")
            appendLine("SUMMARY:${escapeIcsText(input.title)}")
            appendLine("DTSTART;TZID=${input.timeZone.id}:${formatLocal(start)}")
            appendLine("DTEND;TZID=${input.timeZone.id}:${formatLocal(end)}")

            input.notes
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { notes ->
                    appendLine("DESCRIPTION:${escapeIcsText(notes)}")
                }

            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }

        val file = File(context.cacheDir, "calendar_invite_$uid.ics")
        file.writeText(ics)
        return file
    }

    private fun formatUtc(instant: Instant): String =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(instant)

    private fun formatLocal(value: ZonedDateTime): String =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
            .format(value)

    private fun escapeIcsText(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
}