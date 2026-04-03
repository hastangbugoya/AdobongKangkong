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
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedLogExportJsonUseCase
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedNutritionMonthSnapshotJsonUseCase
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedNutritionSnapshotJsonUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SharedSnapshotProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.adobongkangkong.shared"

        private const val SNAPSHOT_PATH_PREFIX = "snapshot"
        private const val SNAPSHOT_MONTH_PATH_PREFIX = "snapshot-month"
        private const val LOGS_PATH = "logs"

        private const val SNAPSHOT_LATEST = 1
        private const val SNAPSHOT_BY_DATE = 2
        private const val SNAPSHOT_MONTH_LATEST = 3
        private const val SNAPSHOT_MONTH_BY_MONTH = 4
        private const val LOGS_RECENT = 5

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/latest", SNAPSHOT_LATEST)
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/*", SNAPSHOT_BY_DATE)

            addURI(AUTHORITY, "$SNAPSHOT_MONTH_PATH_PREFIX/latest", SNAPSHOT_MONTH_LATEST)
            addURI(AUTHORITY, "$SNAPSHOT_MONTH_PATH_PREFIX/*", SNAPSHOT_MONTH_BY_MONTH)

            addURI(AUTHORITY, LOGS_PATH, LOGS_RECENT)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SharedSnapshotProviderEntryPoint {
        fun buildSharedNutritionSnapshotJsonUseCase(): BuildSharedNutritionSnapshotJsonUseCase
        fun buildSharedNutritionMonthSnapshotJsonUseCase(): BuildSharedNutritionMonthSnapshotJsonUseCase
        fun buildSharedLogExportJsonUseCase(): BuildSharedLogExportJsonUseCase
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null

        try {
            val match = uriMatcher.match(uri)
            val path = uri.path.orEmpty().trimStart('/')

            Log.d(
                "SharedSnapshotProvider",
                "openFile uri=$uri authority=${uri.authority} path=$path match=$match"
            )

            val (json, fileName) = when {
                match == SNAPSHOT_LATEST || match == SNAPSHOT_BY_DATE -> {
                    val targetDate = resolveTargetDate(uri)
                    val json = buildSnapshotJson(
                        context = context,
                        date = targetDate
                    )
                    json to "shared_snapshot_${targetDate}.json"
                }

                match == SNAPSHOT_MONTH_LATEST || match == SNAPSHOT_MONTH_BY_MONTH -> {
                    val targetMonth = resolveTargetMonth(uri)
                    val json = buildMonthSnapshotJson(
                        context = context,
                        month = targetMonth
                    )
                    json to "shared_snapshot_month_${targetMonth}.json"
                }

                match == LOGS_RECENT || path == LOGS_PATH -> {
                    val json = buildLogsJson(context)
                    json to "shared_logs.json"
                }

                path == "$SNAPSHOT_PATH_PREFIX/latest" || path.startsWith("$SNAPSHOT_PATH_PREFIX/") -> {
                    val targetDate = resolveTargetDate(uri)
                    val json = buildSnapshotJson(
                        context = context,
                        date = targetDate
                    )
                    json to "shared_snapshot_${targetDate}.json"
                }

                path == "$SNAPSHOT_MONTH_PATH_PREFIX/latest" || path.startsWith("$SNAPSHOT_MONTH_PATH_PREFIX/") -> {
                    val targetMonth = resolveTargetMonth(uri)
                    val json = buildMonthSnapshotJson(
                        context = context,
                        month = targetMonth
                    )
                    json to "shared_snapshot_month_${targetMonth}.json"
                }

                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }

            val file = File(context.cacheDir, fileName)
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

    private fun resolveTargetMonth(uri: Uri): YearMonth {
        return when (uriMatcher.match(uri)) {
            SNAPSHOT_MONTH_LATEST -> YearMonth.now()

            SNAPSHOT_MONTH_BY_MONTH -> {
                val lastSegment = uri.lastPathSegment
                    ?: throw IllegalArgumentException("Missing snapshot month in URI: $uri")

                if (lastSegment == "latest") {
                    YearMonth.now()
                } else {
                    try {
                        YearMonth.parse(lastSegment)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid snapshot month in URI: $uri", e)
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

    private fun buildMonthSnapshotJson(
        context: Context,
        month: YearMonth
    ): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        return runBlocking {
            entryPoint
                .buildSharedNutritionMonthSnapshotJsonUseCase()
                .invoke(
                    month = month,
                    zoneId = ZoneId.systemDefault()
                )
        }
    }

    private fun buildLogsJson(
        context: Context
    ): String {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        return runBlocking {
            entryPoint
                .buildSharedLogExportJsonUseCase()
                .invoke()
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val pfd = openFile(uri, mode) ?: return null
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String {
        val match = uriMatcher.match(uri)
        val path = uri.path.orEmpty().trimStart('/')

        return when {
            match == SNAPSHOT_LATEST ||
                    match == SNAPSHOT_BY_DATE ||
                    match == SNAPSHOT_MONTH_LATEST ||
                    match == SNAPSHOT_MONTH_BY_MONTH ||
                    match == LOGS_RECENT ||
                    path == LOGS_PATH ||
                    path == "$SNAPSHOT_PATH_PREFIX/latest" ||
                    path.startsWith("$SNAPSHOT_PATH_PREFIX/") ||
                    path == "$SNAPSHOT_MONTH_PATH_PREFIX/latest" ||
                    path.startsWith("$SNAPSHOT_MONTH_PATH_PREFIX/") -> "application/json"

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