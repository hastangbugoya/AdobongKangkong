package com.example.adobongkangkong.domain.model

import com.example.adobongkangkong.data.local.db.entity.MealSlot
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import java.time.Instant

data class LogEntry(
    val id: Long = 0,
    val stableId: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val timestamp: Instant,
    val logDateIso: String,
    val itemName: String,
    val foodStableId: String?,
    val nutrients: NutrientMap,

    /**
     * Preserved user-entered amount intent for this log row.
     *
     * These fields are stored alongside the immutable nutrient snapshot so log rows can later be
     * reopened in Quick Add edit mode without reverse-deriving the user's original quantity basis.
     */
    val amount: Double = 1.0,
    val unit: LogUnit = LogUnit.ITEM,

    // ✅ NEW: provenance + UI grouping
    val recipeBatchId: Long? = null,
    val recipeVariantId: Long? = null,
    val gramsPerServingCooked: Double? = null,

    /**
     * Active measured-yield row used when this was logged.
     *
     * This is only populated for recipe gram logs that used RecipeMeasuredYield instead
     * of the legacy cooked-batch path. It lets old logs explain exactly which yield
     * assumption was used even after the recipe's active yield is updated later.
     */
    val measuredYieldIdUsed: Long? = null,

    /**
     * Measured cooked yield, in grams, frozen at log time.
     */
    val measuredYieldGramsUsed: Double? = null,

    /**
     * User-entered/logged grams for measured-yield recipe gram logs.
     */
    val gramsLogged: Double? = null,

    /**
     * Recipe servings equivalent computed from:
     *
     *     gramsLogged / measuredYieldGramsUsed * recipeServingsYield
     *
     * Stored for audit/debug display so future UI does not have to reverse-engineer it.
     */
    val servingsEquivalent: Double? = null,

    val mealSlot: MealSlot? = null
)