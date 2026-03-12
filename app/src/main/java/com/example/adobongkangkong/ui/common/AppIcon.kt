package com.example.adobongkangkong.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.adobongkangkong.ui.theme.AppIconSize

@Composable
fun AppCardIcon(
    resId: Int,
    size: Dp = AppIconSize.CardAction,
    contentDescription: String? = null
) {
    Icon(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = Modifier.size(size)
    )
}