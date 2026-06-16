package com.example.adobongkangkong.ui.common.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.adobongkangkong.domain.nutrition.NutrientCaution
import com.example.adobongkangkong.domain.nutrition.NutrientCautionBasis
import java.util.Locale

@Composable
fun NutrientCautionCard(
    cautions: List<NutrientCaution>,
    modifier: Modifier = Modifier,
    title: String = "Cautions",
    subtitle: String? = null,
) {
    if (cautions.isEmpty()) return

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NutrientCautionMessages(cautions = cautions)
        }
    }
}

@Composable
fun NutrientCautionMessages(
    cautions: List<NutrientCaution>,
    modifier: Modifier = Modifier,
) {
    if (cautions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cautions.forEach { caution ->
            Text(
                text = caution.messageText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun NutrientCaution.messageText(): String {
    val basisText = when (basis) {
        NutrientCautionBasis.LOGGED_AMOUNT -> "this entry"
        NutrientCautionBasis.RECIPE_SERVING -> "one serving"
        NutrientCautionBasis.RECIPE_BATCH -> "the full batch"
    }

    return "${label}: about ${formatAmount(amount)} $unit in $basisText."
}

private fun formatAmount(value: Double): String =
    when {
        value >= 100.0 -> String.format(Locale.US, "%.0f", value)
        value >= 10.0 -> String.format(Locale.US, "%.1f", value).trimTrailingZeros()
        else -> String.format(Locale.US, "%.2f", value).trimTrailingZeros()
    }

private fun String.trimTrailingZeros(): String =
    trimEnd('0').trimEnd('.')
