package com.example.adobongkangkong.domain.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class FoodBannerBlurGenerator {

    /**
     * Policy-locked blur: cheap "pixel blur" via downscale -> upscale.
     * No RenderEffect, no View blur.
     */
    fun generateBlurredDerivative(
        inputJpegFile: File,
        outputWebpFile: File,
        webpQuality: Int = 60,
        downscaleTargetWidthPx: Int = 96
    ) {
        val src = BitmapFactory.decodeFile(inputJpegFile.absolutePath) ?: return

        // Keep aspect ratio
        val targetW = downscaleTargetWidthPx
        val targetH = max(1, (src.height * (targetW.toFloat() / src.width)).toInt())

        val small = Bitmap.createScaledBitmap(src, targetW, targetH, /* filter = */ true)
        val blurred = Bitmap.createScaledBitmap(small, src.width, src.height, /* filter = */ true)

        FileOutputStream(outputWebpFile).use { out ->
            // On API 30+, WEBP_LOSSY exists; WEBP works but may be lossless depending on API.
            @Suppress("DEPRECATION")
            blurred.compress(Bitmap.CompressFormat.WEBP, webpQuality, out)
        }

        // cleanup
        if (blurred != src) blurred.recycle()
        if (small != src) small.recycle()
        src.recycle()
    }
}
