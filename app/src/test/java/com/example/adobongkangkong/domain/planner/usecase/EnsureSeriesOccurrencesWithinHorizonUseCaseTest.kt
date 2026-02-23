package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnsureSeriesOccurrencesWithinHorizonUseCaseTest {

    @Test
    fun `expands non-uniform weekday-slot pattern within window`() {
        // 2026-02-22 is Sunday; 2026-02-23 is Monday
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-03-01")   // Sun

        val rules = listOf(
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.LUNCH),
            rule(seriesId = 100L, weekday = 2, slot = MealSlot.DINNER),
            rule(seriesId = 100L, weekday = 5, slot = MealSlot.BREAKFAST)
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        assertEquals(3, keys.size)
        assertTrue(keys.contains(Triple("2026-02-23", MealSlot.LUNCH, "")))
        assertTrue(keys.contains(Triple("2026-02-24", MealSlot.DINNER, "")))
        assertTrue(keys.contains(Triple("2026-02-27", MealSlot.BREAKFAST, "")))
    }

    @Test
    fun `does not emit entries for weekdays not present in rules`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-02-23")   // Mon only

        val rules = listOf(
            rule(seriesId = 100L, weekday = 2, slot = MealSlot.DINNER) // Tue (not in window)
        )

        val keys = computeTargetKeysForWindow(start, end, rules)
        assertEquals(0, keys.size)
    }

    @Test
    fun `emits multiple meals on same date when multiple rules exist for same weekday`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-02-23")   // Mon only

        val rules = listOf(
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.BREAKFAST),
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.LUNCH)
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        assertEquals(2, keys.size)
        assertTrue(keys.contains(Triple("2026-02-23", MealSlot.BREAKFAST, "")))
        assertTrue(keys.contains(Triple("2026-02-23", MealSlot.LUNCH, "")))
    }

    @Test
    fun `CUSTOM slot includes custom label in key`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-02-23")   // Mon only

        val rules = listOf(
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.CUSTOM, customLabel = "Brunch")
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        assertEquals(1, keys.size)
        assertEquals(Triple("2026-02-23", MealSlot.CUSTOM, "Brunch"), keys.first())
    }

    @Test
    fun `spanning multiple weeks repeats on each matching weekday`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-03-08")   // Sun (2 full weeks)

        val rules = listOf(
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.LUNCH) // Mondays
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        // Mondays in range: 2026-02-23 and 2026-03-02
        assertEquals(2, keys.size)
        assertTrue(keys.contains(Triple("2026-02-23", MealSlot.LUNCH, "")))
        assertTrue(keys.contains(Triple("2026-03-02", MealSlot.LUNCH, "")))
    }

    @Test
    fun `end date is inclusive`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-02-24")   // Tue

        val rules = listOf(
            rule(seriesId = 100L, weekday = 2, slot = MealSlot.DINNER) // Tue should be included
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        assertEquals(1, keys.size)
        assertEquals(Triple("2026-02-24", MealSlot.DINNER, ""), keys.first())
    }

    @Test
    fun `ordering is deterministic by date then by rule order for that weekday`() {
        val start = LocalDate.parse("2026-02-23") // Mon
        val end = LocalDate.parse("2026-02-24")   // Tue

        // Insert order matters: for Monday, Lunch then Breakfast (intentionally reversed)
        val rules = listOf(
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.LUNCH),
            rule(seriesId = 100L, weekday = 1, slot = MealSlot.BREAKFAST),
            rule(seriesId = 100L, weekday = 2, slot = MealSlot.DINNER),
        )

        val keys = computeTargetKeysForWindow(start, end, rules)

        val expected = listOf(
            Triple("2026-02-23", MealSlot.LUNCH, ""),
            Triple("2026-02-23", MealSlot.BREAKFAST, ""),
            Triple("2026-02-24", MealSlot.DINNER, "")
        )
        assertEquals(expected, keys)
    }

    private fun rule(
        seriesId: Long,
        weekday: Int,
        slot: MealSlot,
        customLabel: String? = null,
        id: Long = 0L
    ): PlannedSeriesSlotRuleEntity {
        return PlannedSeriesSlotRuleEntity(
            id = id,
            seriesId = seriesId,
            weekday = weekday,
            slot = slot,
            customLabel = customLabel,
            createdAtEpochMs = 0L
        )
    }
}