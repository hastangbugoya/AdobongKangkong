package com.example.adobongkangkong.ui.dashboard

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.adobongkangkong.core.log.MeowLog
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Temporary Health Connect debug widget for AK's estimated calories-burned MVP.
 *
 * This intentionally logs both aggregate and raw calorie reads for the dashboard-selected date so we can diagnose
 * whether repeated kcal values are coming from Health Connect data, a
 * source app's basal estimate, or an AK fallback path.
 *
 * Debug scope:
 * - TotalCaloriesBurnedRecord aggregate, selected date elapsed/full-day window.
 * - TotalCaloriesBurnedRecord aggregate, midnight -> tomorrow midnight.
 * - ActiveCaloriesBurnedRecord aggregate, selected date elapsed/full-day window.
 * - ActiveCaloriesBurnedRecord aggregate, midnight -> tomorrow midnight.
 * - Raw total and active calorie records grouped by data origin package.
 * - Latest WeightRecord in a recent lookback window.
 *
 * Weight is a point-in-time measurement, so it is read as the latest available
 * record instead of being tied to the dashboard-selected date.
 *
 * Remove or hide this widget before public release.
 */
@Composable
fun HealthConnectPermissionSmokeTest(
    targetDate: LocalDate
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Not checked") }
    var caloriesStatus by remember(targetDate) {
        mutableStateOf("No calorie query yet for $targetDate")
    }
    var weightStatus by remember {
        mutableStateOf("No weight query yet")
    }

    val permissions = remember {
        setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        status = if (grantedPermissions.containsAll(permissions)) {
            "Permission granted"
        } else {
            val missing = permissions - grantedPermissions
            "Permission denied or partial grant. Missing: ${missing.size}"
        }

        MeowLog.d(
            "HealthConnectDebug> permission result " +
                    "granted=${grantedPermissions.joinToString()} " +
                    "required=${permissions.joinToString()} status=$status"
        )
    }

    Column {
        Button(
            onClick = {
                val sdkStatus = HealthConnectClient.getSdkStatus(context)

                if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                    status = "Health Connect unavailable: $sdkStatus"
                    MeowLog.d("HealthConnectDebug> SDK unavailable status=$sdkStatus")
                    return@Button
                }

                val client = HealthConnectClient.getOrCreate(context)

                scope.launch {
                    val granted = client.permissionController.getGrantedPermissions()
                    val alreadyGranted = granted.containsAll(permissions)

                    MeowLog.d(
                        "HealthConnectDebug> permission check " +
                                "alreadyGranted=$alreadyGranted " +
                                "granted=${granted.joinToString()} " +
                                "required=${permissions.joinToString()}"
                    )

                    if (alreadyGranted) {
                        status = "Permission already granted"
                    } else {
                        permissionLauncher.launch(permissions)
                    }
                }
            }
        ) {
            Text("Test Health Connect Permission")
        }

        Text(status)

        Button(
            onClick = {
                scope.launch {
                    caloriesStatus = readTodayTotalCaloriesBurned(
                        context = context,
                        targetDate = targetDate
                    )
                }
            }
        ) {
            Text("Read Calories for Dashboard Date")
        }

        Text(caloriesStatus)

        Button(
            onClick = {
                scope.launch {
                    caloriesStatus = debugTodayTotalCaloriesBurnedRecords(
                        context = context,
                        targetDate = targetDate
                    )
                }
            }
        ) {
            Text("Debug Calorie Sources")
        }

        Button(
            onClick = {
                scope.launch {
                    weightStatus = readLatestHealthConnectWeight(
                        context = context
                    )
                }
            }
        ) {
            Text("Read Latest Health Connect Weight")
        }

        Text(weightStatus)
    }
}

internal suspend fun readTodayTotalCaloriesBurned(
    context: Context,
    targetDate: LocalDate
): String {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
        return "Health Connect unavailable: $sdkStatus"
    }

    val client = HealthConnectClient.getOrCreate(context)
    val totalPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.contains(totalPermission)) {
        return "Total calories permission not granted"
    }

    val zoneId = ZoneId.systemDefault()
    val today = targetDate
    val localToday = LocalDate.now(zoneId)
    val startOfDay = today.atStartOfDay(zoneId).toInstant()
    val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant()
    val queryEnd = if (today == localToday) Instant.now() else endOfDay
    val dateLabel = if (today == localToday) {
        "Today estimated calories used so far"
    } else {
        "Estimated calories used on $today"
    }

    return try {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, queryEnd)
            )
        )

        val energy = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]

        val message = if (energy == null) {
            "No total calories burned data for $today\n\n" +
                    "Aggregate origins are not available in this Health Connect client version. " +
                    "Use Debug Calorie Sources to inspect raw record origins."
        } else {
            "$dateLabel: ${energy.inKilocalories.toInt()} kcal\n\n" +
                    "Aggregate origins are not available in this Health Connect client version. " +
                    "Use Debug Calorie Sources to inspect raw record origins."
        }

        MeowLog.d(
            "HealthConnectDebug> readTodayTotalCaloriesBurned " +
                    "date=$today window=${startOfDay}->$queryEnd energyKcal=${energy?.inKilocalories}"
        )

        message
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> readTodayTotalCaloriesBurned FAILED", t)
        "Calorie query failed: ${t.message ?: t::class.simpleName}"
    }
}

internal suspend fun debugTodayTotalCaloriesBurnedRecords(
    context: Context,
    targetDate: LocalDate
): String {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
        return "Health Connect unavailable: $sdkStatus"
    }

    val client = HealthConnectClient.getOrCreate(context)

    val totalPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    val activePermission = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

    val granted = client.permissionController.getGrantedPermissions()
    val canReadTotal = granted.contains(totalPermission)
    val canReadActive = granted.contains(activePermission)

    val zoneId = ZoneId.systemDefault()
    val today = targetDate
    val localToday = LocalDate.now(zoneId)
    val startOfDay = today.atStartOfDay(zoneId).toInstant()
    val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant()
    val queryEnd = if (today == localToday) Instant.now() else endOfDay

    val lines = mutableListOf<String>()

    fun add(line: String) {
        lines += line
        MeowLog.d("HealthConnectDebug> $line")
    }

    add("Health Connect calorie debug")
    add("Date: $today")
    add("Zone: $zoneId")
    add("Window A: selected-date elapsed/full-day = $startOfDay -> $queryEnd")
    add("Window B: full day = $startOfDay -> $endOfDay")
    add("Can read TotalCaloriesBurnedRecord: $canReadTotal")
    add("Can read ActiveCaloriesBurnedRecord: $canReadActive")

    if (!canReadTotal && !canReadActive) {
        add("No calorie permissions granted.")
        return lines.joinToString("\n")
    }

    if (canReadTotal) {
        appendTotalAggregateDebug(
            client = client,
            start = startOfDay,
            end = queryEnd,
            label = "TOTAL aggregate selected window",
            add = ::add
        )

        appendTotalAggregateDebug(
            client = client,
            start = startOfDay,
            end = endOfDay,
            label = "TOTAL aggregate full day",
            add = ::add
        )

        appendRawTotalRecordDebug(
            client = client,
            start = startOfDay,
            end = endOfDay,
            add = ::add
        )
    } else {
        add("Skipped TotalCaloriesBurnedRecord reads: permission missing.")
    }

    if (canReadActive) {
        appendActiveAggregateDebug(
            client = client,
            start = startOfDay,
            end = queryEnd,
            label = "ACTIVE aggregate selected window",
            add = ::add
        )

        appendActiveAggregateDebug(
            client = client,
            start = startOfDay,
            end = endOfDay,
            label = "ACTIVE aggregate full day",
            add = ::add
        )

        appendRawActiveRecordDebug(
            client = client,
            start = startOfDay,
            end = endOfDay,
            add = ::add
        )
    } else {
        add("Skipped ActiveCaloriesBurnedRecord reads: permission missing.")
    }

    return lines.joinToString("\n")
}

internal suspend fun readLatestHealthConnectWeight(
    context: Context,
    afterInstant: Instant? = null,
    lookbackDays: Long = 30
): String {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
        return "Health Connect unavailable: $sdkStatus"
    }

    val client = HealthConnectClient.getOrCreate(context)
    val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)

    val granted = client.permissionController.getGrantedPermissions()
    if (weightPermission !in granted) {
        return "Weight permission not granted. Tap Test Health Connect Permission and grant Weight access."
    }

    val zoneId = ZoneId.systemDefault()
    val now = Instant.now()
    val start = afterInstant
        ?: LocalDate.now(zoneId)
            .minusDays(lookbackDays)
            .atStartOfDay(zoneId)
            .toInstant()

    return try {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
            ?: return if (afterInstant != null) {
                "No new Health Connect weight records found since ${afterInstant.atZone(zoneId).toLocalDateTime()}."
            } else {
                "No Health Connect weight records found in the last $lookbackDays days."
            }

        val pounds = latest.weight.inPounds
        val source = latest.metadata.dataOrigin.packageName
        val localTime = latest.time.atZone(zoneId).toLocalDateTime()

        buildString {
            appendLine("Latest Health Connect weight:")
            appendLine("%.1f lb".format(pounds))
            appendLine("Time: $localTime")
            appendLine("Source: $source")
            appendLine("Search window: ${start.atZone(zoneId).toLocalDateTime()} -> ${now.atZone(zoneId).toLocalDateTime()}")
        }
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> readLatestHealthConnectWeight FAILED", t)
        "Weight query failed: ${t.message ?: t::class.simpleName}"
    }
}

private suspend fun appendTotalAggregateDebug(
    client: HealthConnectClient,
    start: Instant,
    end: Instant,
    label: String,
    add: (String) -> Unit
) {
    try {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        val energy = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]
        add("$label: ${energy?.inKilocalories?.toInt()?.toString() ?: "null"} kcal")
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> $label FAILED", t)
        add("$label failed: ${t.message ?: t::class.simpleName}")
    }
}

private suspend fun appendActiveAggregateDebug(
    client: HealthConnectClient,
    start: Instant,
    end: Instant,
    label: String,
    add: (String) -> Unit
) {
    try {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        val energy = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
        add("$label: ${energy?.inKilocalories?.toInt()?.toString() ?: "null"} kcal")
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> $label FAILED", t)
        add("$label failed: ${t.message ?: t::class.simpleName}")
    }
}

private suspend fun appendRawTotalRecordDebug(
    client: HealthConnectClient,
    start: Instant,
    end: Instant,
    add: (String) -> Unit
) {
    try {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        add("Raw TotalCaloriesBurnedRecord count: ${response.records.size}")

        if (response.records.isEmpty()) return

        response.records
            .groupBy { it.metadata.dataOrigin.packageName }
            .toSortedMap()
            .forEach { (packageName, records) ->
                val totalKcal = records.sumOf { it.energy.inKilocalories }
                add("Raw total source: $packageName count=${records.size} sum=${totalKcal.toInt()} kcal")

                records.take(12).forEach { record ->
                    add(
                        "  total ${record.energy.inKilocalories.toInt()} kcal | " +
                                "${record.startTime} -> ${record.endTime}"
                    )
                }

                if (records.size > 12) {
                    add("  ... ${records.size - 12} more total records")
                }
            }
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> raw total records FAILED", t)
        add("Raw TotalCaloriesBurnedRecord read failed: ${t.message ?: t::class.simpleName}")
    }
}

private suspend fun appendRawActiveRecordDebug(
    client: HealthConnectClient,
    start: Instant,
    end: Instant,
    add: (String) -> Unit
) {
    try {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        add("Raw ActiveCaloriesBurnedRecord count: ${response.records.size}")

        if (response.records.isEmpty()) return

        response.records
            .groupBy { it.metadata.dataOrigin.packageName }
            .toSortedMap()
            .forEach { (packageName, records) ->
                val totalKcal = records.sumOf { it.energy.inKilocalories }
                add("Raw active source: $packageName count=${records.size} sum=${totalKcal.toInt()} kcal")

                records.take(12).forEach { record ->
                    add(
                        "  active ${record.energy.inKilocalories.toInt()} kcal | " +
                                "${record.startTime} -> ${record.endTime}"
                    )
                }

                if (records.size > 12) {
                    add("  ... ${records.size - 12} more active records")
                }
            }
    } catch (t: Throwable) {
        MeowLog.e("HealthConnectDebug> raw active records FAILED", t)
        add("Raw ActiveCaloriesBurnedRecord read failed: ${t.message ?: t::class.simpleName}")
    }
}