package com.example.adobongkangkong.domain.shared.serialization

import com.example.adobongkangkong.domain.shared.model.SharedNutritionMonthSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedNutritionMonthSnapshotJsonSerializer
 *
 * ## Purpose
 * Encodes a [SharedNutritionMonthSummary] into the compact month-level transport
 * JSON contract consumed by external apps for calendar hydration.
 *
 * ## Design rules
 * - Keep payload small and purpose-built for month calendar use
 * - Export only the 4 core macros by default
 * - Export pinned nutrients in a compact list shape
 * - Do NOT export AdobongKangkong's full internal nutrition/domain model
 *
 * Target shape:
 * {
 *   "monthIso": "2026-03",
 *   "days": [
 *     {
 *       "dateIso": "2026-03-01",
 *       "caloriesKcal": 2150.0,
 *       "proteinG": 165.2,
 *       "carbsG": 201.5,
 *       "fatG": 72.4,
 *       "pinnedNutrients": [
 *         {
 *           "nutrientCode": "fiber",
 *           "amount": 28.4,
 *           "unit": "g"
 *         }
 *       ]
 *     }
 *   ]
 * }
 */
@Singleton
class SharedNutritionMonthSnapshotJsonSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Encodes [summary] into the month-level transport JSON contract.
     */
    fun serialize(summary: SharedNutritionMonthSummary): String {
        val payload = buildJsonObject {
            put("monthIso", summary.monthIso)

            putJsonArray("days") {
                summary.days.forEach { day ->
                    add(
                        buildJsonObject {
                            put("dateIso", day.dateIso)
                            put("caloriesKcal", day.caloriesKcal)
                            put("proteinG", day.proteinG)
                            put("carbsG", day.carbsG)
                            put("fatG", day.fatG)

                            putJsonArray("pinnedNutrients") {
                                day.pinnedNutrients.forEach { nutrient ->
                                    add(
                                        buildJsonObject {
                                            put("nutrientCode", nutrient.nutrientCode)
                                            put("amount", nutrient.amount)
                                            put("unit", nutrient.unit)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        return json.encodeToString(JsonObject.serializer(), payload)
    }
}