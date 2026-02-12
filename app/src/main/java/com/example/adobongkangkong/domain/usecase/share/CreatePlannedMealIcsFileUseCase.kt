// =====================================================
// 3) ICS: generate a single-event .ics file for a meal slot
//    (share it via share sheet using the Uri helper above)
// =====================================================

package com.example.adobongkangkong.domain.usecase.share

import android.content.Context
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class CreatePlannedMealIcsFileUseCase @Inject constructor(
    private val context: Context
) {

    data class Input(
        val date: LocalDate,
        val mealSlotLabel: String,     // used in summary
        val mealName: String,
        val notes: String? = null,     // becomes DESCRIPTION
        val startLocalTime: LocalTime, // you choose mapping for slot
        val durationMinutes: Long = 60,
        val timeZone: ZoneId = ZoneId.systemDefault()
    )

    /**
     * Returns a File in cacheDir. Use FileProvider to share.
     */
    operator fun invoke(input: Input): File {
        val now = Instant.now()
        val dtStart = ZonedDateTime.of(input.date, input.startLocalTime, input.timeZone)
        val dtEnd = dtStart.plusMinutes(input.durationMinutes)

        fun fmtUtc(i: Instant): String =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)
                .withZone(ZoneOffset.UTC)
                .format(i)

        fun fmtZdt(z: ZonedDateTime): String =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)
                .format(z)

        val uid = UUID.randomUUID().toString()
        val dtStamp = fmtUtc(now)
        val summary = "${input.mealSlotLabel} — ${input.mealName}"

        // Basic escaping for ICS text fields
        fun esc(s: String): String =
            s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")

        val description = input.notes?.trim()?.takeIf { it.isNotBlank() }?.let { esc(it) }

        val ics = buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//AdobongKangkong//MealPlan//EN")
            appendLine("CALSCALE:GREGORIAN")
            appendLine("METHOD:PUBLISH")
            appendLine("BEGIN:VEVENT")
            appendLine("UID:$uid")
            appendLine("DTSTAMP:$dtStamp")
            appendLine("SUMMARY:${esc(summary)}")
            appendLine("DTSTART:${fmtZdt(dtStart)}")
            appendLine("DTEND:${fmtZdt(dtEnd)}")
            appendLine("TZID:${input.timeZone.id}") // many clients accept this; some prefer VTIMEZONE blocks
            if (description != null) appendLine("DESCRIPTION:$description")
            appendLine("END:VEVENT")
            appendLine("END:VCALENDAR")
        }

        val file = File(context.cacheDir, "meal_${uid}.ics")
        file.writeText(ics)
        return file
    }
}
