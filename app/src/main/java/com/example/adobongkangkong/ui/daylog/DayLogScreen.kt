package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import com.example.adobongkangkong.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onOpenQuickAdd: (() -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    vm: DayLogViewModel = hiltViewModel()
) {
    val entries by vm.entries.collectAsState()
    val totals by vm.totals.collectAsState()

    val delete = onDelete ?: { id -> vm.deleteEntry(id) }

    LaunchedEffect(date) { vm.load(date) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(date.toString()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.angle_circle_left),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (onOpenQuickAdd != null) {
                        IconButton(onClick = onOpenQuickAdd) {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = "Quick add"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // keeps totals below camera hole
        ) {
            totals?.let { DayTotalsCard(it) }

            LazyColumn {
                items(
                    items = entries,
                    key = { it.logId }
                ) { row ->
                    DayLogRowCard(
                        row = row,
                        onDelete = { delete(row.logId) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
