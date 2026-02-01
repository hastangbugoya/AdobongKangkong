package com.example.adobongkangkong.ui.heatmap

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.ui.heatmap.HeatmapDayDetailsSheet
import com.example.adobongkangkong.ui.heatmap.HeatmapViewModel
import com.example.adobongkangkong.ui.heatmap.MonthHeader
import com.example.adobongkangkong.ui.heatmap.MonthlyHeatmapCalendar
import com.example.adobongkangkong.ui.heatmap.NutrientSelector
import java.time.LocalDate
import com.example.adobongkangkong.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onNavigateToDayLog: (LocalDate) -> Unit,
    onBack: () -> Unit,
    vm: HeatmapViewModel = hiltViewModel()
) {
    val month by vm.month.collectAsState()
    val days by vm.heatmapDays.collectAsState()
    val options by vm.nutrientOptions.collectAsState()
    val selectedNutrient by vm.selectedNutrient.collectAsState()
    val selectedDay by vm.selectedDay.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Optional: when a day is selected, ensure sheet animates open
    LaunchedEffect(selectedDay) {
        if (selectedDay != null) sheetState.show()
    }

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(32.dp))
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(40.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.angle_circle_left),
                contentDescription = "Back"
            )
        }

        Spacer(Modifier.size(8.dp))
        MonthHeader(
            month = month,
            onPrevMonth = vm::goPrevMonth,
            onNextMonth = vm::goNextMonth
        )

        NutrientSelector(
            options = options,
            selected = selectedNutrient,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            onSelect = vm::selectNutrient
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .monthSwipe(
                    onPrev = vm::goPrevMonth,
                    onNext = vm::goNextMonth
                )
        ) {
            MonthlyHeatmapCalendar(
                month = month,
                days = days,
                selectedDate = selectedDay?.date,
                onDayClick = vm::onDayClicked,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }

    if (selectedDay != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = vm::dismissDayDetails
        ) {
            HeatmapDayDetailsSheet(
                day = selectedDay!!,
                nutrientDisplayName = null,
                nutrientUnit = null,
                onViewLogs = onNavigateToDayLog,
                onClose = vm::dismissDayDetails
            )
        }
    }
}

@SuppressLint("ModifierFactoryUnreferencedReceiver")
private fun Modifier.monthSwipe(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    thresholdPx: Float = 120f
): Modifier = pointerInput(Unit) {
    var totalDx = 0f

    detectHorizontalDragGestures(
        onDragEnd = {
            when {
                totalDx > thresholdPx -> onPrev()   // dragged right
                totalDx < -thresholdPx -> onNext()  // dragged left
            }
            totalDx = 0f
        },
        onHorizontalDrag = { _, dx ->
            totalDx += dx
        }
    )
}
