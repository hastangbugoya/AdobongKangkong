package com.example.adobongkangkong.domain.shared.serialization

import com.example.adobongkangkong.domain.shared.model.SharedNutritionSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedNutritionSnapshotJsonSerializer
 *
 * ## Purpose
 * Encodes a [SharedNutritionSnapshot] into JSON for internal/debug exposure.
 *
 * This serializer exists so the shared contract can be:
 * - built on demand,
 * - converted to a stable JSON payload,
 * - exposed later through whatever transport/export path we choose,
 * without coupling JSON details to the builder use case.
 *
 * ## Design rules
 * - Keep this tiny and boring.
 * - This is serialization only; do not add business logic here.
 * - Do not mutate or reinterpret snapshot content here.
 * - Keep contract encoding separate from transport concerns
 *   (files, IPC, content providers, workers, etc.).
 *
 * ## Current intended use
 * - internal debug generation
 * - developer inspection
 * - future producer-side export plumbing
 *
 * ## Evolution notes
 * - The shared contract owns schema versioning.
 * - This serializer should remain generic and not branch on schemaVersion unless
 *   backward-compatibility support is explicitly introduced later.
 */
@Singleton
class SharedNutritionSnapshotJsonSerializer @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Encodes [snapshot] into a JSON string.
     *
     * Output is intended to be stable and human-inspectable for debug/development use.
     */
    fun serialize(snapshot: SharedNutritionSnapshot): String =
        json.encodeToString(snapshot)
}