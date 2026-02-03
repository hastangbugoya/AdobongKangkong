package com.example.adobongkangkong.feature.camera

import android.content.Context
import java.io.File

/**
 * Centralized, policy-locked storage rules for food images.
 *
 * DO NOT add MediaStore, URIs, or gallery logic here.
 * Paths are deterministic and derived from foodId.
 */
class FoodImageStorage(
    private val context: Context
) {

    /* ---------------- Banner (master) ---------------- */

    fun bannerJpegFile(foodId: Long): File =
        File(
            context.filesDir,
            "food_images/$foodId/banner.jpg"
        )

    fun ensureBannerDir(foodId: Long) {
        bannerJpegFile(foodId).parentFile?.mkdirs()
    }

    fun hasBanner(foodId: Long): Boolean =
        bannerJpegFile(foodId).exists()

    fun deleteBanner(foodId: Long) {
        bannerJpegFile(foodId).delete()
        bannerBlurWebpFile(foodId).delete()
    }

    /* ---------------- Banner (blur derivative) ---------------- */

    fun bannerBlurWebpFile(foodId: Long): File =
        File(
            context.cacheDir,
            "food_images/$foodId/banner_blur.webp"
        )

    fun ensureBlurDir(foodId: Long) {
        bannerBlurWebpFile(foodId).parentFile?.mkdirs()
    }

    /* ---------------- Convenience ---------------- */

    fun ensureAllBannerDirs(foodId: Long) {
        ensureBannerDir(foodId)
        ensureBlurDir(foodId)
    }
}