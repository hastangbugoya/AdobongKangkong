package com.example.adobongkangkong.domain.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SharedNutritionGoalProfile
 *
 * ## Purpose
 * Represents the current active nutrition goal profile in AdobongKangkong.
 *
 * This is a **snapshot of user intent**, not historical tracking.
 *
 * It reflects:
 * - current macro targets
 * - all nutrients with configured targets
 *
 * ## Key design rules
 * - This is an export contract, not a persistence model
 * - Built only from existing AK state (no fake goal entities)
 * - No nutrition math here
 * - No internal app logic leakage
 *
 * ## Interoperability intent
 * - HH can compare against its own goals
 * - HH can selectively import values
 * - HH can create/update its own goal entities
 *
 * ## Versioning
 * - Increment [schemaVersion] only for breaking contract changes
 * - Prefer additive optional fields
 */
@Serializable
data class SharedNutritionGoalProfile(
    @SerialName("schemaVersion")
    val schemaVersion: Int,

    @SerialName("exportedAtEpochMs")
    val exportedAtEpochMs: Long,

    @SerialName("source")
    val source: String = "AK_ACTIVE_PROFILE",

    @SerialName("macros")
    val macros: GoalMacroTargets,

    @SerialName("nutrients")
    val nutrients: List<GoalNutrientTarget>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Macro goal targets.
 *
 * Mirrors AK macro structure but focuses only on target ranges.
 */
@Serializable
data class GoalMacroTargets(
    @SerialName("calories")
    val calories: GoalRange,

    @SerialName("protein")
    val protein: GoalRange,

    @SerialName("carbs")
    val carbs: GoalRange,

    @SerialName("fat")
    val fat: GoalRange
)

/**
 * Generic range for a nutrient or macro.
 *
 * Interpretation:
 * - min = lower bound
 * - target = preferred value
 * - max = upper bound
 */
@Serializable
data class GoalRange(
    @SerialName("min")
    val min: Double? = null,

    @SerialName("target")
    val target: Double? = null,

    @SerialName("max")
    val max: Double? = null
)

/**
 * Nutrient goal entry.
 *
 * Only includes nutrients that have configured targets in AK.
 *
 * Includes metadata so consumers do not need AK nutrient DB.
 */
@Serializable
data class GoalNutrientTarget(
    @SerialName("code")
    val code: String,

    @SerialName("name")
    val name: String,

    @SerialName("unit")
    val unit: String,

    @SerialName("min")
    val min: Double? = null,

    @SerialName("target")
    val target: Double? = null,

    @SerialName("max")
    val max: Double? = null,

    @SerialName("isPinnedInAk")
    val isPinnedInAk: Boolean? = null
)