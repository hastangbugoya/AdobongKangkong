package com.example.adobongkangkong.domain.shared.serialization

import com.example.adobongkangkong.domain.shared.model.SharedNutritionSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedNutritionSnapshotJsonSerializer
 *
 * ## Purpose
 * Encodes a [SharedNutritionSnapshot] into the transport JSON contract currently
 * consumed by external apps.
 *
 * ## Important note
 * The domain snapshot model is richer than the current transport payload.
 * This serializer intentionally exports a smaller, stable shape so consumers
 * can read the latest snapshot without needing the full internal model.
 */
@Singleton
class SharedNutritionSnapshotJsonSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Encodes [snapshot] into the current transport JSON contract.
     *
     * Current exported shape:
     * {
     *   "schemaVersion": ...,
     *   "dateIso": "...",
     *   "producedAtEpochMs": ...,
     *   "macros": {
     *     "caloriesKcal": ...,
     *     "proteinG": ...,
     *     "carbsG": ...,
     *     "fatG": ...,
     *     "sugarsG": ...
     *   }
     * }
     */
    fun serialize(snapshot: SharedNutritionSnapshot): String {
        val totals = snapshot.macros.totals

        val payload = buildJsonObject {
            put("schemaVersion", snapshot.schemaVersion)
            put("dateIso", snapshot.dateIso)
            put("producedAtEpochMs", snapshot.producedAtEpochMs)

            putJsonObject("macros") {
                put("caloriesKcal", totals.calories)
                put("proteinG", totals.proteinG)
                put("carbsG", totals.carbsG)
                put("fatG", totals.fatG)
                totals.sugarsG?.let { put("sugarsG", it) }
            }
        }

        return json.encodeToString(JsonObject.serializer(), payload)
    }
}