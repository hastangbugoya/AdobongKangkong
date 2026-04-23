package com.example.adobongkangkong.ui.common.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.ui.food.editor.FoodCategoryUi

/**
 * Reusable category assignment section for food-like editors.
 *
 * Scope:
 * - show available categories
 * - allow selection/deselection
 * - allow creating a new category
 * - optionally expose a manage-categories action
 *
 * Intentionally excluded:
 * - rename/delete dialogs
 * - repository/viewmodel logic
 * - screen-specific state ownership
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryAssignmentSection(
    title: String,
    subtitle: String? = null,
    categories: List<FoodCategoryUi>,
    selectedCategoryIds: Set<Long>,
    newCategoryName: String,
    onCategoryCheckedChange: (Long, Boolean) -> Unit,
    onNewCategoryNameChange: (String) -> Unit,
    onCreateCategory: () -> Unit,
    onOpenManageCategories: (() -> Unit)? = null,
    emptyText: String = "No categories yet.",
    newCategoryLabel: String = "New category",
    addButtonText: String = "Add",
    manageButtonText: String = "Manage categories",
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (categories.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategoryIds.contains(category.id)

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onCategoryCheckedChange(category.id, !isSelected)
                        },
                        label = { Text(category.name) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = onNewCategoryNameChange,
                label = { Text(newCategoryLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onCreateCategory) {
                Text(addButtonText)
            }
        }

        if (onOpenManageCategories != null && categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenManageCategories) {
                    Text(manageButtonText)
                }
            }
        }
    }
}
