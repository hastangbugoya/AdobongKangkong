package com.example.adobongkangkong.ui.common.food

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
import com.example.adobongkangkong.feature.camera.FoodImageStorage
import com.example.adobongkangkong.ui.camera.generateBlurDerivative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FoodBannerCardBackground
 *
 * UI-only reusable background wrapper for list rows/cards that should display the banner blur
 * background (if available), matching FoodsListScreen → FoodRow().
 *
 * Notes:
 * - Master banner: filesDir/food_images/{foodId}/banner.jpg
 * - Blur derivative: cacheDir/food_images/{foodId}/banner_blur.webp (safe to regenerate)
 * - This wrapper draws the blur + scrim behind [content].
 *
 * IMPORTANT:
 * - The child Card/ListItem must use containerColor = Color.Transparent for the blur to be visible.
 */
@Composable
fun FoodBannerCardBackground(
    foodId: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val storage = remember(context) { FoodImageStorage(context) }

    val blurBitmapState = produceState<Bitmap?>(initialValue = null, key1 = foodId) {
        val bannerFile = storage.bannerJpegFile(foodId)
        val blurFile = storage.bannerBlurWebpFile(foodId)

        value = withContext(Dispatchers.IO) {
            if (!blurFile.exists() && bannerFile.exists()) {
                storage.ensureBlurDir(foodId)
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