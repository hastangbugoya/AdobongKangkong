package com.example.adobongkangkong.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedNutritionSnapshotJsonUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

class SharedSnapshotProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.adobongkangkong.shared"

        private const val SNAPSHOT_PATH_PREFIX = "snapshot"
        private const val SNAPSHOT_LATEST = 1
        private const val SNAPSHOT_BY_DATE = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/latest", SNAPSHOT_LATEST)
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/*", SNAPSHOT_BY_DATE)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SharedSnapshotProviderEntryPoint {
        fun buildSharedNutritionSnapshotJsonUseCase(): BuildSharedNutritionSnapshotJsonUseCase
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null

        val targetDate = resolveTargetDate(uri)

        try {
            val json = buildSnapshotJson(
                context = context,
                date = targetDate
            )

            val safeDatePart = targetDate.toString()
            val file = File(context.cacheDir, "shared_snapshot_$safeDatePart.json")
            FileOutputStream(file).use { it.write(json.toByteArray()) }

            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e("SharedSnapshotProvider", "Failed to build snapshot for $uri", e)
            throw e
        }
    }

    private fun resolveTargetDate(uri: Uri): LocalDate {
        return when (uriMatcher.match(uri)) {
            SNAPSHOT_LATEST -> LocalDate.now()

            SNAPSHOT_BY_DATE -> {
                val lastSegment = uri.lastPathSegment
                    ?: throw IllegalArgumentException("Missing snapshot date in URI: $uri")

                if (lastSegment == "latest") {
                    LocalDate.now()
                } else {
                    try {
                        LocalDate.parse(lastSegment)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid snapshot date in URI: $uri", e)
                    }
                }
            }

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    private fun buildSnapshotJson(
        context: Context,
        date: LocalDate
    ): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        return runBlocking {
            entryPoint
                .buildSharedNutritionSnapshotJsonUseCase()
                .invoke(
                    date = date,
                    zoneId = ZoneId.systemDefault()
                )
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val pfd = openFile(uri, mode) ?: return null
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            SNAPSHOT_LATEST,
            SNAPSHOT_BY_DATE -> "application/json"

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}