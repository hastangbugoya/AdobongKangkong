package com.example.adobongkangkong.ui.common.template

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.adobongkangkong.feature.camera.BannerOwnerRef
import com.example.adobongkangkong.feature.camera.BannerOwnerType
import com.example.adobongkangkong.ui.common.banner.BannerCardBackground

@Composable
fun MealTemplateBannerCardBackground(
    templateId: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BannerCardBackground(
        owner = BannerOwnerRef(BannerOwnerType.TEMPLATE, templateId),
        modifier = modifier,
        content = content
    )
}
