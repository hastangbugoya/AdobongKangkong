package com.example.adobongkangkong.domain.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stable shared daily nutrition snapshot contract produced by AdobongKangkong for future
 * sibling-app consumption.
 *
 * ## Purpose
 * This model represents a daily nutrition outcome snapshot. It is intentionally shaped around
 * "what happened" / "what is the day's nutrition state", not around the internal app mechanics
 * that produced it.
 *
 * ## Anti-drift rules
 * - This is an output contract, not an internal persistence model.
 * - Do not expose Room entities, DAO models, UI models, meal logs, recipe structures,
 *   food breakdowns, or supplement item breakdowns here.
 * - Share nouns, not rules:
 *   - shared snapshot = exported outcome contract
 *   - app-specific calculation logic remains app-owned
 * - Keep this schema small, boring, and stable.
 *
 * ## Versioning
 * - [schemaVersion] must be incremented only when the serialized schema shape changes in a way
 *   consumers may need to handle differently.
 * - Additive optional fields are preferred over breaking changes.
 * - Future consumers should tolerate unknown fields and optional sections.
 *
 * ## Current product direction
 * - Macros are first-class and required.
 * - Nutrients are reserved in schema shape now for future expansion, but may be null until a
 *   clean producer path exists.
 *
 * ## Date / time invariants
 * - [dateIso] is the snapshot day in ISO-8601 calendar date form: `YYYY-MM-DD`.
 * - [producedAtEpochMs] is when this snapshot payload was generated, in Unix epoch milliseconds.
 */
@Serializable
data class SharedNutritionSnapshot(
    @SerialName("schemaVersion")
    val schemaVersion: Int,
    @SerialName("dateIso")
    val dateIso: String,
    @SerialName("producedAtEpochMs")
    val producedAtEpochMs: Long,
    @SerialName("macros")
    val macros: MacroSnapshot,
    @SerialName("nutrients")
    val nutrients: NutrientSnapshot? = null
) {
    companion object {
        /**
         * Current schema version for the serialized shared nutrition snapshot contract.
         *
         * Increment only when the wire/schema contract changes in a meaningful way.
         */
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Required macro section of a [SharedNutritionSnapshot].
 *
 * This section is intentionally explicit and macro-specific instead of being modeled as a generic
 * nutrient bag. Macros are the primary cross-app nutrition outcome needed today.
 */
@Serializable
data class MacroSnapshot(
    @SerialName("totals")
    val totals: MacroTotals,
    @SerialName("targets")
    val targets: MacroTargets,
    @SerialName("status")
    val status: MacroStatusSummary,
    @SerialName("sourceBreakdown")
    val sourceBreakdown: MacroSourceBreakdown? = null
)

/**
 * Daily macro totals for the snapshot day.
 *
 * Units:
 * - calories in kcal
 * - protein in grams
 * - carbs in grams
 * - fat in grams
 * - sugars in grams
 *
 * These are outcome totals only. They do not imply how the app internally derived them.
 */
@Serializable
data class MacroTotals(
    @SerialName("calories")
    val calories: Double,
    @SerialName("proteinG")
    val proteinG: Double,
    @SerialName("carbsG")
    val carbsG: Double,
    @SerialName("fatG")
    val fatG: Double,
    @SerialName("sugarsG")
    val sugarsG: Double? = null
)

/**
 * Daily macro target envelope for the snapshot day.
 *
 * The app may conceptually support min / target / max style guidance.
 * Each macro therefore carries an explicit target range model rather than a single number.
 *
 * Null values mean the corresponding bound or target is unknown / not configured.
 */
@Serializable
data class MacroTargets(
    @SerialName("calories")
    val calories: MacroTargetRange,
    @SerialName("protein")
    val protein: MacroTargetRange,
    @SerialName("carbs")
    val carbs: MacroTargetRange,
    @SerialName("fat")
    val fat: MacroTargetRange
)

/**
 * Range/goal representation for one macro.
 *
 * Interpretation:
 * - [min] = lower acceptable bound
 * - [target] = preferred target
 * - [max] = upper acceptable bound
 *
 * Any field may be null if the app does not have that bound configured.
 */
@Serializable
data class MacroTargetRange(
    @SerialName("min")
    val min: Double? = null,
    @SerialName("target")
    val target: Double? = null,
    @SerialName("max")
    val max: Double? = null
)

/**
 * Per-macro daily status summary.
 *
 * This is the consumer-facing outcome classification for each macro.
 * It intentionally hides the app's internal calculation logic.
 */
@Serializable
data class MacroStatusSummary(
    @SerialName("calories")
    val calories: MacroStatus,
    @SerialName("protein")
    val protein: MacroStatus,
    @SerialName("carbs")
    val carbs: MacroStatus,
    @SerialName("fat")
    val fat: MacroStatus
)

/**
 * Consumer-friendly macro status bucket.
 *
 * Keep this stable and small. It is meant to communicate the day's outcome, not internal rules.
 */
@Serializable
enum class MacroStatus {
    @SerialName("unknown")
    UNKNOWN,

    @SerialName("below_min")
    BELOW_MIN,

    @SerialName("on_target")
    ON_TARGET,

    @SerialName("above_max")
    ABOVE_MAX
}

/**
 * Optional macro source breakdown.
 *
 * This is intentionally coarse. It is safe to expose broad outcome categories if already easy to
 * compute from trusted existing totals. Do not expand this into item-level internals.
 *
 * Omit the whole section when the app does not yet have a clean, trusted source split.
 */
@Serializable
data class MacroSourceBreakdown(
    @SerialName("food")
    val food: MacroTotals? = null,
    @SerialName("supplements")
    val supplements: MacroTotals? = null
)

/**
 * Optional future-ready nutrients section.
 *
 * Product direction today is macros-first. This nullable section reserves schema space for future
 * non-macro nutrient outcomes without forcing a top-level redesign later.
 *
 * For now this may remain null until there is a clean and trustworthy producer path.
 */
@Serializable
data class NutrientSnapshot(
    @SerialName("items")
    val items: List<NutrientSnapshotItem> = emptyList()
)

/**
 * Future-ready nutrient item payload.
 *
 * This is intentionally minimal. It exists to reserve schema shape, not to force full nutrient
 * sharing before the app is ready.
 */
@Serializable
data class NutrientSnapshotItem(
    @SerialName("code")
    val code: String,
    @SerialName("name")
    val name: String,
    @SerialName("unit")
    val unit: String,
    @SerialName("total")
    val total: Double? = null,
    @SerialName("target")
    val target: Double? = null,
    @SerialName("status")
    val status: MacroStatus = MacroStatus.UNKNOWN
)