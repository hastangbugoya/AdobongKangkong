package com.example.adobongkangkong.ui.usda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.usda.SearchUsdaFoodsByKeywordsUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsdaSearchScreen(
    onBack: () -> Unit,
    vm: UsdaSearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarMessage by vm.snackbar.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.snackbarShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "USDA Search",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search USDA foods") },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { vm.search() },
                    enabled = !state.isSearching && !state.isImporting
                ) {
                    Text("Search")
                }
            }

            Spacer(Modifier.height(8.dp))

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (state.isSearching) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Searching USDA...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (state.results.isEmpty()) {
                        item {
                            Text(
                                text = if (state.lastSearchedQuery.isNullOrBlank()) {
                                    "Enter keywords to search USDA foods."
                                } else {
                                    "No USDA foods found."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }

                    items(
                        items = state.results,
                        key = { it.fdcId }
                    ) { item ->
                        UsdaSearchResultRow(
                            item = item,
                            enabled = !state.isImporting,
                            onClick = {
                                vm.importSelected(item.fdcId)
                            }
                        )
                        HorizontalDivider()
                    }

                    if (state.isImporting) {
                        item(key = "importing_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsdaSearchResultRow(
    item: SearchUsdaFoodsByKeywordsUseCase.PickItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = item.description.ifBlank { "Unnamed USDA Food" },
                style = MaterialTheme.typography.titleMedium
            )

            if (item.brand.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.brand,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val servingAndPackage = buildList {
                item.householdServingFullText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }

                item.packageWeight
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }.joinToString(" • ")

            if (servingAndPackage.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = servingAndPackage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (item.servingText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.servingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item.dataType?.takeIf { it.isNotBlank() }?.let { dataType ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dataType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            val meta = buildList {
                add("fdcId ${item.fdcId}")
                if (item.gtinUpc.isNotBlank()) add("UPC ${item.gtinUpc}")
            }.joinToString(" • ")

            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
