package com.example.adobongkangkong

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Temporary Health Connect permission rationale screen for AK's estimated
 * calories-burned MVP.
 *
 * AK currently requests read access only for Health Connect total calories
 * burned so the user can optionally display estimated calories used per
 * calendar day. AK does not write health data and does not modify food logs
 * from Health Connect data.
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Health Connect access",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "AdobongKangkong can read your total estimated calories burned from Health Connect only if you grant permission. This is used as optional dashboard context for calories used per calendar day."
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = "AK does not write health data, does not change your food logs, and does not use this data unless you enable the feature."
                    )
                }
            }
        }
    }
}

/**
 * Temporary Health Connect smoke-test UI.
 *
 * This is intentionally not final AK UI. It verifies that AK can request only
 * the TotalCaloriesBurnedRecord read permission, read the current local
 * calendar day's aggregate calorie estimate, and inspect raw records while the
 * Health Connect MVP is being developed.
 */
@Composable
fun HealthConnectPermissionSmokeTest() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Not checked") }
    var caloriesStatus by remember { mutableStateOf("No calorie query yet") }

    val permissions = remember {
        setOf(
            HealthPermission.getReadPermission(
                TotalCaloriesBurnedRecord::class
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        status = if (grantedPermissions.containsAll(permissions)) {
            "Permission granted"
        } else {
            "Permission denied"
        }
    }

    Column {
        Button(
            onClick = {
                val sdkStatus = HealthConnectClient.getSdkStatus(context)

                if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                    status = "Health Connect unavailable: $sdkStatus"
                    return@Button
                }

                val client = HealthConnectClient.getOrCreate(context)

                scope.launch {
                    val alreadyGranted =
                        client.permissionController
                            .getGrantedPermissions()
                            .containsAll(permissions)

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
                    caloriesStatus = readTodayTotalCaloriesBurned(context)
                }
            }
        ) {
            Text("Read Today's Calories Used")
        }

        Button(
            onClick = {
                scope.launch {
                    caloriesStatus = debugTodayTotalCaloriesBurnedRecords(context)
                }
            }
        ) {
            Text("Debug Raw Calorie Records")
        }

        Text(caloriesStatus)
    }
}

/**
 * Reads Health Connect's aggregate TotalCaloriesBurnedRecord value for the
 * current local calendar day.
 *
 * The returned text includes the local midnight-to-midnight query window so
 * early testing can verify that AK is querying the intended date boundary.
 */
internal suspend fun readTodayTotalCaloriesBurned(context: Context): String {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
        return "Health Connect unavailable: $sdkStatus"
    }

    val client = HealthConnectClient.getOrCreate(context)

    val permission = HealthPermission.getReadPermission(
        TotalCaloriesBurnedRecord::class
    )

    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.contains(permission)) {
        return "Permission not granted"
    }

    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val window = todayHealthConnectWindow(today, zoneId)
    val windowText = window.asDisplayText(zoneId)

    return try {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    window.startInstant,
                    window.endInstant
                )
            )
        )

        val energy = response[TotalCaloriesBurnedRecord.ENERGY_TOTAL]

        val originsText = try {
            response.dataOrigins
                .joinToString(separator = "\n") { origin ->
                    origin.packageName
                }
                .ifBlank { "No aggregate origins reported" }
        } catch (e: Exception) {
            "Could not read aggregate origins: ${e.message ?: e::class.simpleName}"
        }

        if (energy == null) {
            "No total calories burned data for today\nWindow: $windowText\n\nOrigins:\n$originsText"
        } else {
            val kcal = energy.inKilocalories
            "Today estimated calories used: ${kcal.toInt()} kcal\nWindow: $windowText\n\nOrigins:\n$originsText"
        }
    } catch (e: Exception) {
        "Calorie query failed: ${e.message ?: e::class.simpleName}\nWindow: $windowText"
    }
}

/**
 * Reads raw TotalCaloriesBurnedRecord rows for the current local day and
 * groups them by Health Connect data origin.
 *
 * This is a temporary debugging helper only. Production AK logic should prefer
 * Health Connect aggregate reads for daily estimated calories burned.
 */
internal suspend fun debugTodayTotalCaloriesBurnedRecords(context: Context): String {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
        return "Health Connect unavailable: $sdkStatus"
    }

    val client = HealthConnectClient.getOrCreate(context)

    val permission = HealthPermission.getReadPermission(
        TotalCaloriesBurnedRecord::class
    )

    val granted = client.permissionController.getGrantedPermissions()
    if (!granted.contains(permission)) {
        return "Permission not granted"
    }

    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val window = todayHealthConnectWindow(today, zoneId)
    val windowText = window.asDisplayText(zoneId)

    return try {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    window.startInstant,
                    window.endInstant
                )
            )
        )

        if (response.records.isEmpty()) {
            return "No raw TotalCaloriesBurnedRecord records for today\nWindow: $windowText"
        }

        val rows = response.records
            .groupBy { it.metadata.dataOrigin.packageName }
            .entries
            .joinToString(separator = "\n\n") { entry ->
                val packageName = entry.key
                val totalKcal = entry.value.sumOf { it.energy.inKilocalories }

                val records = entry.value.joinToString(separator = "\n") { record ->
                    val kcal = record.energy.inKilocalories.toInt()
                    "$kcal kcal | ${record.startTime} → ${record.endTime}"
                }

                "$packageName total: ${totalKcal.toInt()} kcal\n$records"
            }

        "Raw TotalCaloriesBurnedRecord records\nWindow: $windowText\n\n$rows"
    } catch (e: Exception) {
        "Raw calorie debug failed: ${e.message ?: e::class.simpleName}\nWindow: $windowText"
    }
}

private data class HealthConnectDayWindow(
    val startInstant: Instant,
    val endInstant: Instant
)

private fun todayHealthConnectWindow(
    date: LocalDate,
    zoneId: ZoneId
): HealthConnectDayWindow {
    return HealthConnectDayWindow(
        startInstant = date
            .atStartOfDay(zoneId)
            .toInstant(),
        endInstant = date
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
    )
}

private fun HealthConnectDayWindow.asDisplayText(zoneId: ZoneId): String {
    val startText = startInstant
        .atZone(zoneId)
        .toLocalDateTime()
        .toString()

    val endText = endInstant
        .atZone(zoneId)
        .toLocalDateTime()
        .toString()

    return "$startText → $endText"
}
