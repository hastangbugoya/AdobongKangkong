package com.example.adobongkangkong.ui.common

import androidx.compose.foundation.lazy.LazyListScope
import com.example.adobongkangkong.domain.model.NutrientCategory

fun <T> LazyListScope.sectionedByCategory(
    items: List<T>,
    categoryOf: (T) -> NutrientCategory,
    keyOf: (index: Int, item: T) -> Any,
    header: @androidx.compose.runtime.Composable (NutrientCategory) -> Unit,
    row: @androidx.compose.runtime.Composable (index: Int, item: T) -> Unit
) {
    var lastCategory: NutrientCategory? = null

    items.forEachIndexed { index, item ->
        val category = categoryOf(item)

        if (category != lastCategory) {
            item(key = "header_${category.name}") {
                header(category)
            }
            lastCategory = category
        }

        item(key = keyOf(index, item)) {
            row(index, item)
        }
    }
}
