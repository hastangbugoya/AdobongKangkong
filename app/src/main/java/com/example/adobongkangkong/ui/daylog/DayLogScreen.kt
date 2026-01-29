package com.example.adobongkangkong.ui.daylog


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.adobongkangkong.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(
    date: LocalDate,
    onBack: () -> Unit,
    vm: DayLogViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val logRows by vm.logRows.collectAsState()
    val totals by vm.totals.collectAsState()

    LaunchedEffect(date) {
        vm.load(date)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.trash), contentDescription = "Back")
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

            totals?.let {
                DayTotalsCard(it)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logRows) { row ->
                    DayLogRowCard(row)
                }
            }
        }
    }
}

