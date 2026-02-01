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
    vm: StartupViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.start()
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                state.isWorking -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                }

                state.error != null -> {
                    Text(
                        text = state.message ?: "Unknown Error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::retry) {
                        Text("Retry")
                    }
                }

                state.isDone -> {
                    // Usually navigation happens via LaunchedEffect,
                    // but you can show a fallback message if you want
                    Text("Ready", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

}

