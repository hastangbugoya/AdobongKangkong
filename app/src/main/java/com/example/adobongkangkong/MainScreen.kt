package com.example.adobongkangkong

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet

@Composable
fun MainScreen() {
    var showQuickAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Text("+")
            }
        }
    ) { padding ->
        // Dashboard stays visible under the sheet
        DashboardScreen(
            modifier = Modifier.padding(padding)
        )

        if (showQuickAdd) {
            QuickAddBottomSheet(onDismiss = { showQuickAdd = false })
        }
    }
}