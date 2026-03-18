package com.example.adobongkangkong.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.adobongkangkong.domain.model.ServingUnit
import java.util.UUID

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["name"]),
        Index(value = ["isRecipe"]),

        // USDA traceability lookups (safe even if null)
        Index(value = ["usdaGtinUpc"]),
        Index(value = ["usdaFdcId"], unique = true),
        Index(value = ["mergedIntoFoodId"]),
    ]
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Stable identifier used for export/import reconciliation.
     * This must never change once created.
     */
    val stableId: String = UUID.randomUUID().toString(),

    val name: String,
    val brand: String? = null,

    /**
     * User-facing serving model.
     *
     * Examples:
     * - 1 G (mass-based foods)
     * - 240 ML (volume-based foods)
     * - 1 PACKET (custom unit), optionally mass-backed via gramsPerServingUnit
     * - 1 CAN (custom unit), optionally volume-backed via mlPerServingUnit
     */
    val servingSize: Double = 1.0,
    val servingUnit: ServingUnit = ServingUnit.G,

    /**
     * Optional display text (e.g., USDA householdServingFullText like "1 cup", "2 tbsp",
     * or user-entered like "1 packet").
     *
     * Display-only; do not use for math.
     */
    val householdServingText: String? = null,

    val servingsPerPackage: Double? = null,

    /**
     * Optional mass backing for non-gram serving units.
     *
     * Meaning:
     *   1 × servingUnit == gramsPerServingUnit grams
     *
     * Examples:
     * - servingUnit=PACKET, gramsPerServingUnit=28.0  => 1 packet = 28g
     * - servingUnit=CUP,    gramsPerServingUnit=246.0 => 1 cup = 246g (user-provided; no density guessing)
     *
     * Notes:
     * - If servingUnit == G, this should typically be null.
     * - Never infer grams from mL (no density guessing).
     */
    val gramsPerServingUnit: Double? = null,

    /**
     * Optional volume backing for non-mL serving units.
     *
     * Meaning:
     *   1 × servingUnit == mlPerServingUnit milliliters
     *
     * Examples:
     * - servingUnit=CAN,    mlPerServingUnit=473.0  => 1 can = 473 mL
     * - servingUnit=BOTTLE, mlPerServingUnit=500.0  => 1 bottle = 500 mL
     *
     * Notes:
     * - If servingUnit == ML, this should typically be null.
     * - Never infer mL from grams (no density guessing).
     */
    val mlPerServingUnit: Double? = null,

    // Treat recipes as foods so they can be logged identically
    val isRecipe: Boolean = false,
    val isLowSodium: Boolean? = null,

    // -------------------------
    // Soft delete
    // -------------------------

    /**
     * Soft delete flag.
     *
     * - Default "Delete" should set this to true and hide the food from normal search/lists.
     * - Logs are safe because they snapshot itemName + nutrientsJson and store foodStableId.
     */
    val isDeleted: Boolean = false,

    /**
     * Optional deletion timestamp for debugging / future "Recently deleted" UI.
     * Stored as epoch millis (nullable).
     */
    val deletedAtEpochMs: Long? = null,

    // -------------------------
    // USDA traceability (optional)
    // -------------------------

    val usdaFdcId: Long? = null,
    val usdaGtinUpc: String? = null,
    val usdaPublishedDate: String? = null,
    val usdaModifiedDate: String? = null,

    /**
     * USDA-reported serving preserved for traceability even if user edits the food later.
     */
    val usdaServingSize: Double? = null,
    val usdaServingUnit: ServingUnit? = null,
    val usdaHouseholdServingText: String? = null,

    /**
     * If non-null, this food has been merged into another canonical food.
     */
    val mergedIntoFoodId: Long? = null,

    /**
     * Timestamp for when this food was merged into another canonical food.
     */
    val mergedAtEpochMs: Long? = null,

    /**
     * Count of foods that have been merged into this food.
     *
     * Semantics:
     * - 0 => ordinary food, or a food that has never been used as a merge target
     * - >0 => canonical merge target / fallback food for one or more merged variants
     *
     * Why count instead of boolean:
     * - keeps UI logic cheap (`mergeChildCount > 0`)
     * - preserves useful information for future badges/debugging/analytics
     * - avoids repeated "who merged into me?" queries for simple list indicators
     *
     * Current intended write rule:
     * - increment when another food is merged into this food
     *
     * Current intended read rule:
     * - treat `mergeChildCount > 0` as "show layered/canonical indicator"
     */
    val mergeChildCount: Int = 0
)

/**
 * =============================================================================
 * FUTURE-YOU / FUTURE AI NOTES — DO NOT DELETE
 * =============================================================================
 *
 * Merge child count design
 * - mergeChildCount intentionally replaces the lighter boolean idea.
 * - Use `mergeChildCount > 0` to identify a canonical merge target in UI.
 * - This is meant to keep read paths cheap and future-proof.
 *
 * Intended merge semantics
 * - Child/override food:
 *     - mergedIntoFoodId = canonicalFoodId
 *     - mergedAtEpochMs = now
 * - Canonical target food:
 *     - mergeChildCount increments by 1 for each newly merged child
 *
 * Important guardrail
 * - Do not silently reinterpret mergeChildCount as a fully recomputed truth unless
 *   that migration/refactor is intentional and documented.
 * - For now this field is operational state maintained by merge logic.
 *
 * Future plans — do not remove
 * - UI may later show the exact merge count badge, not just a layered icon.
 * - Admin/debug tools may use mergeChildCount to locate canonical foods quickly.
 * - If unmerge support is added later, decide explicitly whether counts are decremented
 *   transactionally or rebuilt from mergedIntoFoodId relationships.
 *
 * IMPORTANT TO FUTURE AI:
 * - Do NOT delete these future plans during cleanup or refactors.
 * - They are intentional reminders for later merge-feature evolution.
 */