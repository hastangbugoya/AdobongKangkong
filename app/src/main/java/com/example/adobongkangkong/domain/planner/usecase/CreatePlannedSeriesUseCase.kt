package com.example.adobongkangkong.domain.planner.usecase

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEndConditionType
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesEntity
import com.example.adobongkangkong.data.local.db.entity.PlannedSeriesSlotRuleEntity
import com.example.adobongkangkong.domain.repository.PlannedSeriesRepository
import javax.inject.Inject

class CreatePlannedSeriesUseCase @Inject constructor(
    private val repo: PlannedSeriesRepository
) {

    data class SlotRuleInput(
        val weekday: Int,          // 1=Mon..7=Sun
        val slot: MealSlot,
        val customLabel: String? = null
    )

    data class Input(
        val effectiveStartDateIso: String,   // yyyy-MM-dd
        val effectiveEndDateIso: String? = null,
        val endConditionType: String,        // PlannedSeriesEndConditionType.*
        val endConditionValue: String? = null,
        val slotRules: List<SlotRuleInput>
    )

    suspend fun execute(input: Input): Long {
        validate(input)

        val now = System.currentTimeMillis()

        val seriesId = repo.insertSeries(
            PlannedSeriesEntity(
                effectiveStartDate = input.effectiveStartDateIso,
                effectiveEndDate = input.effectiveEndDateIso,
                endConditionType = input.endConditionType,
                endConditionValue = input.endConditionValue,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        val rules = input.slotRules.map { r ->
            PlannedSeriesSlotRuleEntity(
                seriesId = seriesId,
                weekday = r.weekday,
                slot = r.slot,
                customLabel = r.customLabel,
                createdAtEpochMs = now
            )
        }

        repo.replaceSlotRules(seriesId, rules)
        return seriesId
    }

    private fun validate(input: Input) {
        require(input.slotRules.isNotEmpty()) { "slotRules cannot be empty" }

        require(
            input.endConditionType == PlannedSeriesEndConditionType.UNTIL_DATE ||
                    input.endConditionType == PlannedSeriesEndConditionType.REPEAT_COUNT ||
                    input.endConditionType == PlannedSeriesEndConditionType.INDEFINITE
        ) { "Invalid endConditionType: ${input.endConditionType}" }

        when (input.endConditionType) {
            PlannedSeriesEndConditionType.UNTIL_DATE -> require(!input.endConditionValue.isNullOrBlank()) {
                "endConditionValue (until date ISO) required for UNTIL_DATE"
            }
            PlannedSeriesEndConditionType.REPEAT_COUNT -> require(!input.endConditionValue.isNullOrBlank()) {
                "endConditionValue (repeat count) required for REPEAT_COUNT"
            }
            PlannedSeriesEndConditionType.INDEFINITE -> {
                // endConditionValue should be null; allow but ignore if passed
            }
        }

        input.slotRules.forEach { r ->
            require(r.weekday in 1..7) { "weekday must be 1..7, was ${r.weekday}" }
            if (r.slot == MealSlot.CUSTOM) {
                require(!r.customLabel.isNullOrBlank()) { "customLabel required when slot == CUSTOM" }
            }
        }

        // Prevent duplicates like (Mon,LUNCH) appearing twice
        val dedupeKey = input.slotRules.map { it.weekday to it.slot }
        require(dedupeKey.size == dedupeKey.distinct().size) { "Duplicate (weekday, slot) rules not allowed" }
    }
}