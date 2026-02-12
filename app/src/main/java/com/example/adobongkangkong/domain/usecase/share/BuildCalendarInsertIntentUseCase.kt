// =====================================================
// 4) “Add to calendar” (no permissions): ACTION_INSERT intent
//    User can add guests/invites in the calendar app UI.
// =====================================================

package com.example.adobongkangkong.domain.usecase.share

import android.content.Intent
import android.provider.CalendarContract
import java.time.*
import javax.inject.Inject

class BuildCalendarInsertIntentUseCase @Inject constructor() {

    data class Input(
        val date: LocalDate,
        val startLocalTime: LocalTime,
        val durationMinutes: Long = 60,
        val mealSlotLabel: String,
        val mealName: String,
        val description: String? = null,
        val timeZone: ZoneId = ZoneId.systemDefault()
    )

    operator fun invoke(input: Input): Intent {
        val start = ZonedDateTime.of(input.date, input.startLocalTime, input.timeZone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(input.date, input.startLocalTime, input.timeZone)
            .plusMinutes(input.durationMinutes)
            .toInstant()
            .toEpochMilli()

        val title = "${input.mealSlotLabel} — ${input.mealName}"

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            input.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        }
    }
}

