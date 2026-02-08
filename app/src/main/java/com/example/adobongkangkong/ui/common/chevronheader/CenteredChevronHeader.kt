package com.example.adobongkangkong.ui.common.chevronheader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CenteredChevronHeader(
    text: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevIcon: Painter,
    nextIcon: Painter,
    prevContentDescription: String,
    nextContentDescription: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    verticalPadding: Dp = 6.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrev) {
            Icon(painter = prevIcon, contentDescription = prevContentDescription)
        }

        Spacer(Modifier.width(spacing))

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.width(spacing))

        IconButton(onClick = onNext) {
            Icon(painter = nextIcon, contentDescription = nextContentDescription)
        }
    }
}

