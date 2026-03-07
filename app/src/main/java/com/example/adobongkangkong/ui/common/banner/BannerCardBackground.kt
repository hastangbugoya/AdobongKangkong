package com.example.adobongkangkong.ui.common.banner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.adobongkangkong.feature.camera.BannerOwnerRef
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.generateBlurDerivative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generic banner background wrapper for any entity that participates in the shared banner system.
 *
 * Current supported owners:
 * - FOOD
 * - TEMPLATE
 */
@Composable
fun BannerCardBackground(
    owner: BannerOwnerRef,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }

    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = owner) {
        val bannerFile = storage.bannerJpegFile(owner)
        val blurFile = storage.bannerBlurWebpFile(owner)

        value = withContext(Dispatchers.IO) {
            if (!blurFile.exists() && bannerFile.exists()) {
                storage.ensureBlurDir(owner)
                generateBlurDerivative(
                    inputJpeg = bannerFile,
                    outputWebp = blurFile,
                    webpQuality = 60,
                    downscaleTargetWidthPx = 96
                )
            }

            if (blurFile.exists()) BitmapFactory.decodeFile(blurFile.absolutePath) else null
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        blurBitmapState.value?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.22f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.06f))
            )
        }

        content()
    }
}
