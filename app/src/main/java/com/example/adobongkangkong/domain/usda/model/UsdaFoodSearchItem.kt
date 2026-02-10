package com.example.adobongkangkong.domain.usda.model
/**
 * DTO representing a single food item returned by the USDA Foods Search API.
 *
 * Purpose
 * -------
 * This class is a *pure data transfer object* that mirrors the shape of USDA
 * search JSON responses. It exists solely to deserialize external API data
 * into a typed Kotlin structure.
 *
 * This is NOT:
 * - a domain model
 * - a Room entity
 * - a mutable application object
 *
 * It must never be stored directly or referenced outside the USDA import path.
 *
 * Usage
 * -----
 * - Parsed by the USDA search/parser layer.
 * - Consumed by ImportUsdaFoodFromSearchJsonUseCase.
 * - Converted exactly once into the domain model [Food].
 *
 * Architectural boundary
 * ----------------------
 * UsdaFoodSearchItem represents the *edge of the system*.
 * All validation, normalization, and persistence must occur when converting
 * this DTO into domain models. No business logic should depend on this class.
 *
 * Versioning notes
 * ----------------
 * - [fdcId] is the stable USDA identifier for the food.
 * - [publishedDate] is treated as the primary version gate when deciding
 *   whether USDA data is newer than what is already stored locally.
 * - [modifiedDate] is informational and secondary.
 *
 * Design rules
 * ------------
 * - Fields should be a superset of what the USDA API may return.
 * - All fields are nullable except [fdcId] to tolerate incomplete payloads.
 * - If the USDA API adds fields, they should be added here rather than
 *   duplicated elsewhere.
 *
 * Do not create additional UsdaFoodSearchItem classes in other files.
 */
import com.example.adobongkangkong.data.usda.UsdaFoodNutrient
import kotlinx.serialization.Serializable
/**
 * ⚠️ IMPORTANT – USDA DTO BOUNDARY
 *
 * This usecase is the ONLY place where USDA search DTOs
 * (e.g. [UsdaFoodSearchItem]) are allowed to be consumed.
 *
 * Rules:
 * - Do NOT define local copies of USDA DTOs in this file.
 * - Do NOT pass raw USDA JSON beyond this boundary.
 * - Do NOT let domain logic, UI, or repositories depend on USDA DTOs.
 *
 * All USDA data must be:
 *   USDA JSON → UsdaFoodSearchItem (DTO) → Food (domain) → FoodEntity (DB)
 *
 * Versioning:
 * - `publishedDate` is the primary version gate for USDA data freshness.
 * - `modifiedDate` is secondary and informational.
 *
 * If you need additional USDA fields:
 * - Extend the canonical DTO in `domain/usda/model/UsdaFoodSearchItem`
 * - Map the new fields here when constructing the domain `Food`
 *
 * Violating these rules will cause subtle bugs and duplicate-model drift.
 */
@Serializable
data class UsdaFoodSearchItem(
    val fdcId: Long,
    val description: String? = null,
    val dataType: String? = null,
    val gtinUpc: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val ingredients: String? = null,

    // USDA identity / versioning
    val publishedDate: String? = null,
    val modifiedDate: String? = null,

    // Serving info
    val servingSizeUnit: String? = null,
    val servingSize: Double? = null,
    val householdServingFullText: String? = null,
    val packageWeight: String? = null,

    // Classification
    val foodCategory: String? = null,

    // Nutrients
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)