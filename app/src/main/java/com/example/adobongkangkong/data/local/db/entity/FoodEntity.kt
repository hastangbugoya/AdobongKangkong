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
        Index(value = ["usdaFdcId"]),
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
    // USDA traceability (optional)
    // -------------------------

    val usdaFdcId: Long? = null,
    val usdaGtinUpc: String? = null,
    val usdaPublishedDate: String? = null,

    /**
     * USDA-reported serving preserved for traceability even if user edits the food later.
     */
    val usdaServingSize: Double? = null,
    val usdaServingUnit: ServingUnit? = null,
    val usdaHouseholdServingText: String? = null
)
