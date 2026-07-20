package com.example.adobongkangkong.domain.weight

import java.time.Instant
import java.time.LocalDate

/**
 * Domain model for a raw body-weight measurement.
 *
 * Multiple measurements can exist on the same date. Reports should not use all
 * measurements directly. They should use the selected daily BodyWeightLog trend
 * value, or a future selection rule that chooses one measurement per day.
 */
data class BodyWeightMeasurement(
    val id: Long = 0L,
    val date: LocalDate,
    val measuredAt: Instant,
    val weightKg: Double,
    val source: BodyWeightMeasurementSource,
    val sourcePackage: String? = null,
    val sourceRecordId: String? = null,
    val importedAt: Instant? = null,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Source of a raw body-weight measurement.
 */
enum class BodyWeightMeasurementSource {
    MANUAL,
    HEALTH_CONNECT,
    LEGACY_WEIGHT_LOG
}
