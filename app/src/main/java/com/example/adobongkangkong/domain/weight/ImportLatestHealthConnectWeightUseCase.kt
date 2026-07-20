package com.example.adobongkangkong.domain.weight

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Imports the latest body-weight reading available from Health Connect.
 *
 * AK stores imported scale weights in two layers:
 * - [BodyWeightMeasurement] keeps the raw Health Connect reading.
 * - [BodyWeightLog] remains the single official daily trend value.
 *
 * MVP import rules:
 * - Read the latest [WeightRecord] from a recent lookback window.
 * - Skip a Health Connect record that was already imported.
 * - Skip likely duplicates from the same source within the configured duplicate
 *   window and tolerance.
 * - Keep same-day imported readings only when they are meaningfully separated,
 *   using the configured minimum import gap.
 * - If the imported day has no daily trend value yet, create one and point it
 *   to the imported measurement.
 * - If the imported day already has a daily trend value, leave it unchanged.
 *
 * Future trend-selection support:
 * - The default daily trend rule is stored in [UserPreferencesRepository].
 * - Imported readings are preserved even when they do not immediately replace
 *   the daily trend value.
 * - A later selector can re-evaluate same-day measurements using the stored
 *   trend rule, such as closest to preferred weigh-in time.
 */
class ImportLatestHealthConnectWeightUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BodyWeightLogRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val zoneId: ZoneId
) {

    suspend operator fun invoke(
        displayUnit: BodyWeightUnit,
        lookbackDays: Long = DEFAULT_LOOKBACK_DAYS,
        createDailyTrendWhenMissing: Boolean = true
    ): Result {
        val duplicateWindowMinutes =
            userPreferencesRepository.weightImportDuplicateWindowMinutes.value
                .coerceAtLeast(1)
                .toLong()

        val duplicateToleranceKg =
            userPreferencesRepository.weightImportDuplicateToleranceKg.value
                .coerceAtLeast(0.0)

        val minimumSameDayGapMinutes =
            userPreferencesRepository.weightImportMinimumGapMinutes.value
                .coerceAtLeast(0)
                .toLong()

        val trendSelectionMethod =
            userPreferencesRepository.weightTrendSelectionMethod.value

        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            return Result.HealthConnectUnavailable(sdkStatus)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)
        val granted = client.permissionController.getGrantedPermissions()

        if (weightPermission !in granted) {
            return Result.PermissionMissing
        }

        val now = Instant.now()
        val start = LocalDate.now(zoneId)
            .minusDays(lookbackDays.coerceAtLeast(1L))
            .atStartOfDay(zoneId)
            .toInstant()

        val latest = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            ).records.maxByOrNull { it.time }
        } catch (t: Throwable) {
            return Result.Error(t.message ?: t::class.simpleName.orEmpty())
        } ?: return Result.NoWeightRecords(lookbackDays = lookbackDays)

        val measuredAt = latest.time
        val date = measuredAt.atZone(zoneId).toLocalDate()
        val weightKg = latest.weight.inKilograms
        val sourcePackage = latest.metadata.dataOrigin.packageName
        val sourceRecordId = latest.metadata.id.takeIf { it.isNotBlank() }

        val existingBySourceRecord = sourceRecordId?.let { id ->
            repository.getMeasurementBySourceRecordId(
                source = BodyWeightMeasurementSource.HEALTH_CONNECT,
                sourceRecordId = id
            )
        }

        if (existingBySourceRecord != null) {
            return Result.AlreadyImported(
                date = existingBySourceRecord.date,
                measuredAt = existingBySourceRecord.measuredAt
            )
        }

        val nearDuplicate = repository.findNearDuplicateMeasurement(
            date = date,
            source = BodyWeightMeasurementSource.HEALTH_CONNECT,
            sourcePackage = sourcePackage,
            measuredAt = measuredAt,
            weightKg = weightKg,
            duplicateWindowMinutes = duplicateWindowMinutes,
            duplicateToleranceKg = duplicateToleranceKg
        )

        if (nearDuplicate != null) {
            return Result.NearDuplicate(
                date = nearDuplicate.date,
                measuredAt = nearDuplicate.measuredAt
            )
        }

        val nearestSameDay = repository.getNearestMeasurementOnDate(
            date = date,
            measuredAt = measuredAt
        )

        if (nearestSameDay != null) {
            val gapMinutes = absoluteMinutesBetween(
                a = nearestSameDay.measuredAt,
                b = measuredAt
            )

            if (gapMinutes < minimumSameDayGapMinutes) {
                return Result.TooCloseToExisting(
                    date = nearestSameDay.date,
                    existingMeasuredAt = nearestSameDay.measuredAt,
                    gapMinutes = gapMinutes,
                    requiredGapMinutes = minimumSameDayGapMinutes
                )
            }
        }

        val measurementId = repository.insertMeasurement(
            BodyWeightMeasurement(
                date = date,
                measuredAt = measuredAt,
                weightKg = weightKg,
                source = BodyWeightMeasurementSource.HEALTH_CONNECT,
                sourcePackage = sourcePackage,
                sourceRecordId = sourceRecordId,
                importedAt = now,
                note = HEALTH_CONNECT_IMPORT_NOTE,
                isDeleted = false,
                createdAt = now,
                updatedAt = now
            )
        )

        val existingTrend = repository.getByDate(date)
        val usedAsDailyTrend = createDailyTrendWhenMissing && existingTrend == null

        if (usedAsDailyTrend) {
            repository.upsertByDate(
                date = date,
                weight = weightKg.toDisplayWeight(displayUnit),
                unit = displayUnit,
                note = HEALTH_CONNECT_IMPORT_NOTE,
                selectedMeasurementId = measurementId,
                trendSelectionMethod = trendSelectionMethod,
                isTrendUserOverride = false
            )

            userPreferencesRepository.setWeightLogLastPromptResetEpochDay(date.toEpochDay())
        }

        return Result.Imported(
            measurementId = measurementId,
            date = date,
            measuredAt = measuredAt,
            weightKg = weightKg,
            sourcePackage = sourcePackage,
            usedAsDailyTrend = usedAsDailyTrend
        )
    }

    sealed interface Result {
        data class Imported(
            val measurementId: Long,
            val date: LocalDate,
            val measuredAt: Instant,
            val weightKg: Double,
            val sourcePackage: String?,
            val usedAsDailyTrend: Boolean
        ) : Result

        data class HealthConnectUnavailable(val sdkStatus: Int) : Result
        object PermissionMissing : Result
        data class NoWeightRecords(val lookbackDays: Long) : Result
        data class AlreadyImported(val date: LocalDate, val measuredAt: Instant) : Result
        data class NearDuplicate(val date: LocalDate, val measuredAt: Instant) : Result

        data class TooCloseToExisting(
            val date: LocalDate,
            val existingMeasuredAt: Instant,
            val gapMinutes: Long,
            val requiredGapMinutes: Long
        ) : Result

        data class Error(val message: String) : Result
    }
}

private fun Double.toDisplayWeight(unit: BodyWeightUnit): Double =
    when (unit) {
        BodyWeightUnit.KG -> this
        BodyWeightUnit.LB -> this * POUNDS_PER_KILOGRAM
    }

private fun absoluteMinutesBetween(a: Instant, b: Instant): Long {
    val diffMs = kotlin.math.abs(a.toEpochMilli() - b.toEpochMilli())
    return diffMs / MILLIS_PER_MINUTE
}

private const val HEALTH_CONNECT_IMPORT_NOTE = "Imported from Health Connect"
private const val DEFAULT_LOOKBACK_DAYS = 30L
private const val POUNDS_PER_KILOGRAM = 2.2046226218
private const val MILLIS_PER_MINUTE = 60_000L
