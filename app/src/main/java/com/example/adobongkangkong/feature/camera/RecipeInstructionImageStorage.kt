package com.example.adobongkangkong.feature.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * App-owned storage helper for recipe instruction step images.
 *
 * Purpose
 * - Import an image from a caller-provided [Uri]
 * - Re-encode/compress it into app internal storage
 * - Return a stable relative path suitable for Room persistence
 * - Delete step images when removed/replaced
 *
 * Storage rules
 * - Master files live in filesDir only
 * - DB stores only a relative path, never absolute file paths
 * - One image per instruction step for now
 *
 * Current file layout
 * - filesDir/recipe_instruction_images/{recipeId}/{stepStableId}/step.jpg
 *
 * Notes
 * - This helper is intentionally separate from [FoodImageStorage] to avoid banner/media
 *   coupling while still following the same app-owned internal-storage policy.
 * - Future variants (multiple images, thumbnails, annotations) can build on this layout.
 */
class RecipeInstructionImageStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val ROOT_DIR = "recipe_instruction_images"
        private const val FILE_NAME = "step.jpg"
        private const val JPEG_QUALITY = 85
        private const val MAX_DIMENSION_PX = 1600
    }

    /**
     * Returns the app-owned relative DB path for a step image.
     */
    fun relativeImagePath(
        recipeId: Long,
        stepStableId: String
    ): String = "$ROOT_DIR/$recipeId/$stepStableId/$FILE_NAME"

    /**
     * Returns the on-disk File for a stored relative image path.
     */
    fun fileForRelativePath(relativePath: String): File =
        File(context.filesDir, relativePath)

    /**
     * Returns the canonical File location for a recipe step image.
     */
    fun imageFile(
        recipeId: Long,
        stepStableId: String
    ): File = fileForRelativePath(
        relativeImagePath(
            recipeId = recipeId,
            stepStableId = stepStableId
        )
    )

    /**
     * Ensures the parent directory for the recipe step image exists.
     */
    fun ensureStepImageDir(
        recipeId: Long,
        stepStableId: String
    ) {
        imageFile(recipeId, stepStableId).parentFile?.mkdirs()
    }

    /**
     * Returns true if the stored file exists.
     */
    fun hasImage(
        recipeId: Long,
        stepStableId: String
    ): Boolean = imageFile(recipeId, stepStableId).exists()

    /**
     * Imports an external image Uri, compresses it into app internal storage,
     * and returns the relative path to store in Room.
     *
     * Existing file at the target location is replaced.
     */
    fun importImage(
        sourceUri: Uri,
        recipeId: Long,
        stepStableId: String
    ): String {
        val targetFile = imageFile(
            recipeId = recipeId,
            stepStableId = stepStableId
        )
        targetFile.parentFile?.mkdirs()

        val bitmap = decodeScaledBitmap(
            resolver = context.contentResolver,
            uri = sourceUri,
            maxDimensionPx = MAX_DIMENSION_PX
        ) ?: error("Unable to decode instruction image from Uri: $sourceUri")

        FileOutputStream(targetFile).use { output ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            check(ok) { "Failed to compress instruction image to ${targetFile.absolutePath}" }
            output.flush()
        }

        return relativeImagePath(
            recipeId = recipeId,
            stepStableId = stepStableId
        )
    }

    /**
     * Deletes the step image for the given recipe/stable step id.
     * Safe to call if already missing.
     *
     * @return true if a file existed and was deleted
     */
    fun deleteImage(
        recipeId: Long,
        stepStableId: String
    ): Boolean {
        val file = imageFile(recipeId, stepStableId)
        val deleted = file.exists() && file.delete()
        file.parentFile?.listFiles()?.takeIf { it.isEmpty() }?.let { file.parentFile?.delete() }
        file.parentFile?.parentFile?.listFiles()?.takeIf { it.isEmpty() }?.let {
            file.parentFile?.parentFile?.delete()
        }
        return deleted
    }

    /**
     * Deletes a step image by stored relative path.
     * Safe to call if path is null or file is already missing.
     *
     * @return true if a file existed and was deleted
     */
    fun deleteImageByRelativePath(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) return false
        val file = fileForRelativePath(relativePath)
        val deleted = file.exists() && file.delete()
        file.parentFile?.listFiles()?.takeIf { it.isEmpty() }?.let { file.parentFile?.delete() }
        file.parentFile?.parentFile?.listFiles()?.takeIf { it.isEmpty() }?.let {
            file.parentFile?.parentFile?.delete()
        }
        return deleted
    }

    private fun decodeScaledBitmap(
        resolver: ContentResolver,
        uri: Uri,
        maxDimensionPx: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = computeInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimensionPx = maxDimensionPx
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }

    private fun computeInSampleSize(
        width: Int,
        height: Int,
        maxDimensionPx: Int
    ): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height

        while (currentWidth > maxDimensionPx || currentHeight > maxDimensionPx) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }
}