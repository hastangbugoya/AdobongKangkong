package com.example.adobongkangkong.ui.dashboard

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
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import com.example.adobongkangkong.debugTodayTotalCaloriesBurnedRecords
import com.example.adobongkangkong.readTodayTotalCaloriesBurned
import kotlinx.coroutines.launch

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

        Text(caloriesStatus)

        Button(
            onClick = {
                scope.launch {
                    caloriesStatus = debugTodayTotalCaloriesBurnedRecords(context)
                }
            }
        ) {
            Text("Debug Calorie Sources")
        }
    }
}