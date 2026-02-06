package com.example.adobongkangkong.ui.common.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GoalFlagsSection(
    favorite: Boolean,
    eatMore: Boolean,
    limit: Boolean,
    onToggleFavorite: (Boolean) -> Unit,
    onToggleEatMore: (Boolean) -> Unit,
    onToggleLimit: (Boolean) -> Unit,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Flags", style = MaterialTheme.typography.titleMedium)

        if (isTablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FlagCheck("Favorite", favorite, onToggleFavorite, Modifier.weight(1f))
                FlagCheck("Eat more", eatMore, onToggleEatMore, Modifier.weight(1f))
                FlagCheck("Limit", limit, onToggleLimit, Modifier.weight(1f))
            }
        } else {
            FlagCheck("Favorite", favorite, onToggleFavorite, Modifier.fillMaxWidth())
            FlagCheck("Eat more", eatMore, onToggleEatMore, Modifier.fillMaxWidth())
            FlagCheck("Limit", limit, onToggleLimit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FlagCheck(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .clickable { onChecked(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}
