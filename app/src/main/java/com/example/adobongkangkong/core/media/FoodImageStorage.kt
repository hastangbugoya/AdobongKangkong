package com.example.adobongkangkong.feature.camera

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Centralized, policy-locked storage rules for food images.
 *
 * DO NOT add MediaStore, URIs, or gallery logic here.
 * Paths are deterministic and derived from foodId.
 */
class FoodImageStorage @Inject constructor(
    @ApplicationContext private val context: Context
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

    /**
    * Deletes all cached blur derivatives. Safe because they can be regenerated.
    * @return number of files deleted
    */
    fun deleteAllBlurCache(): Int {
        val root = File(context.cacheDir, "food_images")
        if (!root.exists() || !root.isDirectory) return 0

        var deleted = 0
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val blur = File(dir, "banner_blur.webp")
            if (blur.exists() && blur.delete()) deleted++
            // best-effort: remove empty directory
            dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
        }
        return deleted
    }

    /**
     * Finds food ids that have a banner.jpg on disk (filesDir).
     */
    fun findFoodIdsWithBannerInFilesDir(): List<Long> {
        val root = File(context.filesDir, "food_images")
        if (!root.exists() || !root.isDirectory) return emptyList()

        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val id = dir.name.toLongOrNull() ?: return@mapNotNull null
                val banner = File(dir, "banner.jpg")
                if (banner.exists()) id else null
            }
            ?: emptyList()
    }

    /**
     * Deletes all food media dirs for a given food id (files + cache).
     * Safe to call if already missing.
     * @return number of files deleted (best-effort)
     */
    fun deleteAllFoodMediaDirs(foodId: Long): Int {
        var deleted = 0

        // filesDir
        run {
            val dir = File(context.filesDir, "food_images/$foodId")
            val banner = File(dir, "banner.jpg")
            if (banner.exists() && banner.delete()) deleted++
            // remove dir if empty
            dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
        }

        // cacheDir
        run {
            val dir = File(context.cacheDir, "food_images/$foodId")
            val blur = File(dir, "banner_blur.webp")
            if (blur.exists() && blur.delete()) deleted++
            dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
        }

        return deleted
    }
}