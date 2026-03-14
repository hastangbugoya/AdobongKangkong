package com.example.adobongkangkong.ui.daylog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.R
import com.example.adobongkangkong.ui.common.food.FoodBannerCardBackground
import com.example.adobongkangkong.ui.daylog.model.DayLogRow
import com.example.adobongkangkong.ui.format.toPrettyTime
import com.example.adobongkangkong.ui.theme.AppIconSize
import kotlin.math.roundToInt

@Composable
fun DayLogRowCard(
    row: DayLogRow,
    onClick: () -> Unit,
    onLogAgainToday: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val cardContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(onClick = onClick),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            color = if (row.bannerFoodId != null) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${row.timestamp.toPrettyTime()} • ${row.caloriesKcal?.roundToInt() ?: "0"} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text =
                            "Protein ${row.proteinG?.format1() ?: "0.0"}g  " +
                                    "Carbs ${row.carbsG?.format1() ?: "0.0"}g  " +
                                    "Fat ${row.fatG?.format1() ?: "0.0"}g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    IconButton(
                        onClick = { isMenuExpanded = true }
                    ) {
                        Text(
                            text = "⋮",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.size(AppIconSize.CardAction)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                isMenuExpanded = false
                                onClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Log again today") },
                            onClick = {
                                isMenuExpanded = false
                                onLogAgainToday()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.trash),
                                    contentDescription = null,
                                    modifier = Modifier.size(AppIconSize.CardAction)
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }

    val bannerFoodId = row.bannerFoodId
    if (bannerFoodId != null) {
        FoodBannerCardBackground(foodId = bannerFoodId) { cardContent() }
    } else {
        cardContent()
    }
}

private fun Double.format1(): String =
    ((this * 10.0).roundToInt() / 10.0).toString()
