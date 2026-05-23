package com.example.adobongkangkong.ui.daynutrients

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reusable day-level nutrient breakdown screen.
 *
 * This screen is intentionally date-driven so callers can open it from:
 * - Day Log
 * - Dashboard
 * - Calendar/day detail surfaces
 *
 * Data rules:
 * - ViewModel uses ObserveDailyNutritionTotalsUseCase.
 * - Totals come from immutable logged nutrient snapshots.
 * - No current Food/Recipe recomputation happens here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayNutrientsScreen(
    date: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DayNutrientsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(date) {
        vm.load(date)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Day Nutrients") },
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
        ) {
            Text(
                text = formatDayNutrientsDate(date),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            DayNutrientsContent(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Reusable content body for a full screen or future bottom sheet.
 */
@Composable
fun DayNutrientsContent(
    state: DayNutrientsState,
    modifier: Modifier = Modifier,
    onNutrientClick: (DayNutrientRowUiModel) -> Unit = {}
) {
    if (state.isEmpty) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No nutrient totals for this day",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Log foods for this date to see the full nutrient breakdown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 0.dp,
            bottom = 32.dp
        )
    ) {
        state.sections.forEach { section ->
            item(
                key = "section_${section.title}"
            ) {
                DayNutrientsSectionHeader(title = section.title)
            }

            items(
                items = section.rows,
                key = { row -> row.code }
            ) { row ->
                DayNutrientRow(
                    row = row,
                    onClick = { onNutrientClick(row) }
                )
                HorizontalDivider()
            }

            item(
                key = "spacer_${section.title}"
            ) {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DayNutrientsSectionHeader(
    title: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun DayNutrientRow(
    row: DayNutrientRowUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = row.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = row.amountWithUnit,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private fun formatDayNutrientsDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern(
        "EEE, MMM d, yyyy",
        Locale.getDefault()
    )
    return date.format(formatter)
}