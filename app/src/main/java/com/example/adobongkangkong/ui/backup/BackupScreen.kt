package com.example.adobongkangkong.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R

//BackupScreen(
//onBack = { navController.popBackStack() },
//onRequestRestartApp = {
//    // simplest, effective:
//    kotlin.system.exitProcess(0)
//}
//)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onRequestRestartApp: (() -> Unit)? = null,
    vm: BackupViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) vm.exportTo(uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importFrom(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Export a migration bundle (DB + banner images) and import it on a new phone.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { createBackupLauncher.launch("adobongkangkong_backup.zip") },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export backup (.zip)")
            }

            OutlinedButton(
                onClick = { openBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import backup (.zip)")
            }

            if (state.isBusy) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }

            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (state.needsRestart) {
        AlertDialog(
            onDismissRequest = { vm.dismissRestartPrompt() },
            title = { Text("Restart required") },
            text = {
                Text(
                    "Import finished. Restart the app so Room reopens the restored database cleanly."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.dismissRestartPrompt()
                        onRequestRestartApp?.invoke()
                    }
                ) {
                    Text("Restart now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.dismissRestartPrompt() }) {
                    Text("Later")
                }
            }
        )
    }
}