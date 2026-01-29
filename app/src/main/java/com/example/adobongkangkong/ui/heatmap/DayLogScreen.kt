package com.example.adobongkangkong.ui.heatmap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.mapper.toDayLogRow
import com.example.adobongkangkong.ui.daylog.DayLogRowCard
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    date: LocalDate,
    vm: DayLogViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val entries by vm.entries.collectAsState()
    val totals by vm.totals.collectAsState()

    LaunchedEffect(date) {
        vm.load(date)
    }
    TopAppBar(
        title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painter = painterResource(R.drawable.trash), contentDescription = null)
            }
        }
    )
    Column(Modifier.fillMaxSize()) {
        totals?.let {
            DayTotalsSummary(it)
        }

        LazyColumn {
            items(entries) { entry ->
                DayLogRowCard(entry.toDayLogRow())
                HorizontalDivider()
            }
        }
    }
}
