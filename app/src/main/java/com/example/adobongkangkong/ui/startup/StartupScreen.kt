package com.example.adobongkangkong.ui.startup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun StartupScreen(
    onDone: () -> Unit,
    vm: StartupViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.start()
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("working=${state.isWorking} done=${state.isDone}")
            Text("msg=${state.message ?: "null"}")
            Text("err=${state.error ?: "null"}")
            Spacer(Modifier.height(16.dp))

            when {
                state.isWorking -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                }

                state.error != null -> {
                    // Show last known status message (if any) + the actual error
                    if (!state.message.isNullOrBlank()) {
                        Text(text = state.message!!)
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = state.error ?: "Unknown Error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::retry) {
                        Text("Retry")
                    }
                }

                state.isDone -> {
                    // Usually navigation happens immediately; this is just a fallback.
                    Text("Ready")
                }

                else -> {
                    Text(state.message ?: "Starting…")
                }
            }
        }
    }
}
