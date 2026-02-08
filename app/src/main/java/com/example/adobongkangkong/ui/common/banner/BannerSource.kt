package com.example.adobongkangkong.ui.common.banner

import android.net.Uri
import androidx.annotation.DrawableRes

sealed interface BannerSource {
    data class UriBanner(val uri: Uri) : BannerSource
    data class ResourceBanner(@DrawableRes val resId: Int) : BannerSource
}