package com.example.adobongkangkong

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.adobongkangkong.ui.dashboard.DashboardScreen
import com.example.adobongkangkong.ui.log.QuickAddBottomSheet
import com.example.adobongkangkong.ui.recipe.RecipeBuilderScreen

private enum class RootScreen { DASHBOARD, RECIPE_BUILDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var showQuickAdd by remember { mutableStateOf(false) }
    var rootScreen by remember { mutableStateOf(RootScreen.DASHBOARD) }

    when (rootScreen) {
        RootScreen.DASHBOARD -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("AdobongKangkong") },
                        actions = {
                            TextButton(onClick = { rootScreen = RootScreen.RECIPE_BUILDER }) {
                                Text("Recipes")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { showQuickAdd = true }) { Text("+") }
                }
            ) { padding ->
                DashboardScreen(modifier = Modifier.padding(padding))

                if (showQuickAdd) {
                    QuickAddBottomSheet(onDismiss = { showQuickAdd = false })
                }
            }
        }

        RootScreen.RECIPE_BUILDER -> {
            RecipeBuilderScreen(onBack = { rootScreen = RootScreen.DASHBOARD })
        }
    }
}