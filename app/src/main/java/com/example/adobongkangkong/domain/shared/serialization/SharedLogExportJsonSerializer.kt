package com.example.adobongkangkong.domain.shared.serialization

import com.example.adobongkangkong.data.local.db.entity.LogEntryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedLogExportJsonSerializer
 *
 * ## Purpose
 * Encodes raw LogEntryEntity rows into a stable cross-app transport JSON contract.
 *
 * ## Design rules
 * - Keep payload minimal (no internal model leakage)
 * - Export only fields needed for import/upsert in consumer apps
 * - Preserve stableId + modifiedAt for sync correctness
 *
 * Target shape:
 * {
 *   "logs": [
 *     {
 *       "stableId": "...",
 *       "timestamp": 1711556040000,
 *       "logDateIso": "2026-03-27",
 *       "itemName": "...",
 *       "mealSlot": "DINNER",
 *       "nutrientsJson": "{...}",
 *       "modifiedAt": 1711557000000
 *     }
 *   ]
 * }
 */
@Singleton
class SharedLogExportJsonSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Serializes log rows into the shared log export JSON contract.
     *
     * Important snapshot rule:
     * - This exports the persisted [LogEntryEntity.nutrientsJson] value exactly as stored.
     * - It does not recalculate food or recipe nutrition from current app data.
     * - This preserves historical log accuracy when foods or recipes are edited later.
     *
     * Sync rule:
     * - [LogEntryEntity.stableId] is exported so consumer apps can upsert the same log
     *   instead of creating duplicates.
     * - [LogEntryEntity.modifiedAt] is exported so newer edits can win during sync/import.
     *
     * Null/optional behavior:
     * - [LogEntryEntity.mealSlot] is omitted when null.
     *
     * @param logs raw persisted log rows to export.
     * @return pretty-printed JSON containing schemaVersion and logs array.
     */
    fun serialize(logs: List<LogEntryEntity>): String {
        val payload = buildJsonObject {
            put("schemaVersion", 1)

            put("logs", buildJsonArray {
                logs.forEach { log ->
                    add(
                        buildJsonObject {
                            put("stableId", log.stableId)
                            put("timestamp", log.timestamp.toEpochMilli())
                            put("logDateIso", log.logDateIso)
                            put("itemName", log.itemName)
                            log.mealSlot?.let { put("mealSlot", it.name) }
                            put("nutrientsJson", log.nutrientsJson)
                            put("modifiedAt", log.modifiedAt.toEpochMilli())
                        }
                    )
                }
            })
        }

        return json.encodeToString(JsonObject.serializer(), payload)
    }
}