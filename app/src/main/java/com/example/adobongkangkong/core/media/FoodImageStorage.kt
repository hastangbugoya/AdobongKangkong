package com.example.adobongkangkong.feature.camera

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Centralized, policy-locked storage rules for banner images.
 *
 * Historical note:
 * - The class name stays [FoodImageStorage] because that is already referenced across the app.
 * - The implementation now also supports meal template banners via [BannerOwnerRef].
 *
 * Rules:
 * - Master banner files live in filesDir.
 * - Blur derivatives live in cacheDir and are safe to regenerate.
 * - Paths are deterministic from the entity id.
 */
class FoodImageStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /* ---------------- Food banner API (existing call sites) ---------------- */

    fun bannerJpegFile(foodId: Long): File =
        File(context.filesDir, "food_images/$foodId/banner.jpg")

    fun ensureBannerDir(foodId: Long) {
        bannerJpegFile(foodId).parentFile?.mkdirs()
    }

    fun hasBanner(foodId: Long): Boolean =
        bannerJpegFile(foodId).exists()

    fun deleteBanner(foodId: Long) {
        bannerJpegFile(foodId).delete()
        bannerBlurWebpFile(foodId).delete()
    }

    fun bannerBlurWebpFile(foodId: Long): File =
        File(context.cacheDir, "food_images/$foodId/banner_blur.webp")

    fun ensureBlurDir(foodId: Long) {
        bannerBlurWebpFile(foodId).parentFile?.mkdirs()
    }

    fun ensureAllBannerDirs(foodId: Long) {
        ensureBannerDir(foodId)
        ensureBlurDir(foodId)
    }

    /* ---------------- Shared owner-based API ---------------- */

    fun bannerJpegFile(owner: BannerOwnerRef): File =
        when (owner.type) {
            BannerOwnerType.FOOD -> bannerJpegFile(owner.id)
            BannerOwnerType.TEMPLATE -> File(
                context.filesDir,
                "meal_template_images/${owner.id}/banner.jpg"
            )
        }

    fun bannerBlurWebpFile(owner: BannerOwnerRef): File =
        when (owner.type) {
            BannerOwnerType.FOOD -> bannerBlurWebpFile(owner.id)
            BannerOwnerType.TEMPLATE -> File(
                context.cacheDir,
                "meal_template_images/${owner.id}/banner_blur.webp"
            )
        }

    fun ensureBannerDir(owner: BannerOwnerRef) {
        bannerJpegFile(owner).parentFile?.mkdirs()
    }

    fun ensureBlurDir(owner: BannerOwnerRef) {
        bannerBlurWebpFile(owner).parentFile?.mkdirs()
    }

    fun ensureAllBannerDirs(owner: BannerOwnerRef) {
        ensureBannerDir(owner)
        ensureBlurDir(owner)
    }

    fun hasBanner(owner: BannerOwnerRef): Boolean =
        bannerJpegFile(owner).exists()

    fun deleteBanner(owner: BannerOwnerRef) {
        bannerJpegFile(owner).delete()
        bannerBlurWebpFile(owner).delete()
    }

    /* ---------------- Existing cleanup helpers ---------------- */

    /**
     * Deletes all cached blur derivatives. Safe because they can be regenerated.
     * @return number of files deleted
     */
    fun deleteAllBlurCache(): Int {
        val roots = listOf(
            File(context.cacheDir, "food_images"),
            File(context.cacheDir, "meal_template_images")
        )
        var deleted = 0
        roots.forEach { root ->
            if (!root.exists() || !root.isDirectory) return@forEach
            root.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val blur = File(dir, "banner_blur.webp")
                if (blur.exists() && blur.delete()) deleted++
                dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
            }
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

        run {
            val dir = File(context.filesDir, "food_images/$foodId")
            val banner = File(dir, "banner.jpg")
            if (banner.exists() && banner.delete()) deleted++
            dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
        }

        run {
            val dir = File(context.cacheDir, "food_images/$foodId")
            val blur = File(dir, "banner_blur.webp")
            if (blur.exists() && blur.delete()) deleted++
            dir.listFiles()?.takeIf { it.isEmpty() }?.let { dir.delete() }
        }

        return deleted
    }
}
