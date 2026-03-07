package com.example.adobongkangkong.ui.templates

import androidx.compose.runtime.Composable

@Composable
fun MealTemplateListRoute(
    onBack: () -> Unit,
    onOpenTemplate: (Long) -> Unit,
    onCreateTemplate: () -> Unit
) {
    MealTemplateListScreen(
        onBack = onBack,
        onOpenTemplate = onOpenTemplate,
        onCreateTemplate = onCreateTemplate
    )
}
