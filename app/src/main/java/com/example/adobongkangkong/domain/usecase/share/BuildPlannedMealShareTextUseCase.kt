package com.example.adobongkangkong.domain.usecase.share
// =====================================================
// 1) Share sheet: build a human-friendly message payload
// =====================================================
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class BuildPlannedMealShareTextUseCase @Inject constructor() {

    data class Input(
        val date: LocalDate,
        val mealSlotLabel: String, // "Breakfast", "Lunch", etc.
        val mealName: String,
        val notes: String? = null,
        val itemsSummary: String? = null, // e.g. "Chicken breast, rice, broccoli"
        val macrosSummary: String? = null, // e.g. "650 kcal • P 55g • C 60g • F 18g"
        val deepLink: String? = null // optional app link
    )

    operator fun invoke(input: Input): String {
        val dateStr = input.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))

        val lines = buildList {
            add("${input.mealSlotLabel} — ${input.mealName}")
            add(dateStr)

            input.itemsSummary?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("")
                add("Menu: $it")
            }

            input.macrosSummary?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("")
                add("Macros: $it")
            }

            input.notes?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("")
                add("Notes: $it")
            }

            input.deepLink?.trim()?.takeIf { it.isNotBlank() }?.let {
                add("")
                add(it)
            }
        }

        return lines.joinToString(separator = "\n")
    }
}

// =====================================================
// Banner image attachment note (no extra moving parts):
// - If you already have a content:// Uri for the banner image, pass it as attachmentUri
//   to BuildShareSheetIntentUseCase.
// - If you only have a File or ByteArray, write it to cacheDir and convert to Uri via FileToShareUriUseCase.
// - Use MIME type image/* if you want to be stricter (optional).
// =====================================================

