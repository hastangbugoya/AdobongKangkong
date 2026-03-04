package com.example.adobongkangkong.ui.planner.templatepicker

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adobongkangkong.data.local.db.entity.MealSlot

@Composable
fun MealTemplatePickerRoute(
    dateIso: String,
    initialSlotContext: MealSlot?,
    onBack: () -> Unit,
    onPicked: (Long) -> Unit,
    viewModel: MealTemplatePickerViewModel = hiltViewModel(),
) {
    MealTemplatePickerScreen(
        state = viewModel.state,
        onEvent = { e ->
            when (e) {
                MealTemplatePickerEvent.Back -> onBack()
                is MealTemplatePickerEvent.UpdateQuery -> viewModel.onEvent(e)
                is MealTemplatePickerEvent.SelectTemplate -> onPicked(e.templateId)
            }
        },
        dateIso = dateIso
    )
}
