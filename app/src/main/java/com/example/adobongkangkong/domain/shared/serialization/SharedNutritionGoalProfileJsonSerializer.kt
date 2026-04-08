package com.example.adobongkangkong.domain.shared.serialization

import com.example.adobongkangkong.domain.shared.model.GoalRange
import com.example.adobongkangkong.domain.shared.model.SharedNutritionGoalProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedNutritionGoalProfileJsonSerializer
 *
 * ## Purpose
 * Encodes a [SharedNutritionGoalProfile] into the stable cross-app transport JSON
 * contract for AK's current active nutrition goal profile.
 *
 * ## Design rules
 * - Keep payload purpose-built and stable
 * - Do not leak internal persistence or UI models
 * - Export one current active profile snapshot, not fake goal history
 * - Omit null fields from the wire format when possible
 *
 * Current exported shape:
 * {
 *   "schemaVersion": 1,
 *   "exportedAtEpochMs": 1711557000000,
 *   "source": "AK_ACTIVE_PROFILE",
 *   "macros": {
 *     "calories": { "min": ..., "target": ..., "max": ... },
 *     "protein":  { "min": ..., "target": ..., "max": ... },
 *     "carbs":    { "min": ..., "target": ..., "max": ... },
 *     "fat":      { "min": ..., "target": ..., "max": ... }
 *   },
 *   "nutrients": [
 *     {
 *       "code": "VITAMIN_C_MG",
 *       "name": "Vitamin C",
 *       "unit": "mg",
 *       "min": ...,
 *       "target": ...,
 *       "max": ...,
 *       "isPinnedInAk": true
 *     }
 *   ]
 * }
 */
@Singleton
class SharedNutritionGoalProfileJsonSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun serialize(profile: SharedNutritionGoalProfile): String {
        val payload = buildJsonObject {
            put("schemaVersion", profile.schemaVersion)
            put("exportedAtEpochMs", profile.exportedAtEpochMs)
            put("source", profile.source)

            putJsonObject("macros") {
                putRange("calories", profile.macros.calories)
                putRange("protein", profile.macros.protein)
                putRange("carbs", profile.macros.carbs)
                putRange("fat", profile.macros.fat)
            }

            putJsonArray("nutrients") {
                profile.nutrients.forEach { nutrient ->
                    add(
                        buildJsonObject {
                            put("code", nutrient.code)
                            put("name", nutrient.name)
                            put("unit", nutrient.unit)
                            nutrient.min?.let { put("min", it) }
                            nutrient.target?.let { put("target", it) }
                            nutrient.max?.let { put("max", it) }
                            nutrient.isPinnedInAk?.let { put("isPinnedInAk", it) }
                        }
                    )
                }
            }
        }

        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun JsonObjectBuilder.putRange(
        key: String,
        range: GoalRange
    ) {
        putJsonObject(key) {
            range.min?.let { put("min", it) }
            range.target?.let { put("target", it) }
            range.max?.let { put("max", it) }
        }
    }
}