package com.example.adobongkangkong.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.adobongkangkong.core.log.MeowLog
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedLogExportJsonUseCase
import com.example.adobongkangkong.domain.shared.usecase.BuildSharedNutritionGoalProfileJsonUseCase
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

/**
 * SharedSnapshotProvider
 *
 * Provides JSON snapshots (daily, monthly, goals, logs) via ContentProvider.
 *
 * 🧠 Logging Rationale (VERY IMPORTANT)
 *
 * This class sits at a **cross-app boundary**:
 * - Called externally via ContentResolver
 * - No direct UI feedback if something fails
 * - Failures often appear as:
 *   - "file not found"
 *   - "null cursor"
 *   - silent failure in consuming app
 *
 * Therefore:
 * - MeowLogs are added at **entry, routing, generation, and file write boundaries**
 * - Logs include:
 *   - URI + match code
 *   - resolved date/month
 *   - output file path + size
 * - Errors MUST log stacktrace because caller cannot see them
 *
 * Goal:
 * 👉 Make cross-app failures debuggable using MeowLogs alone
 */
class SharedSnapshotProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.adobongkangkong.shared"

        private const val SNAPSHOT_PATH_PREFIX = "snapshot"
        private const val SNAPSHOT_MONTH_PATH_PREFIX = "snapshot-month"
        private const val GOALS_PATH_PREFIX = "goals"
        private const val LOGS_PATH = "logs"

        private const val SNAPSHOT_LATEST = 1
        private const val SNAPSHOT_BY_DATE = 2
        private const val SNAPSHOT_MONTH_LATEST = 3
        private const val SNAPSHOT_MONTH_BY_MONTH = 4
        private const val LOGS_RECENT = 5
        private const val GOALS_CURRENT = 6

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/latest", SNAPSHOT_LATEST)
            addURI(AUTHORITY, "$SNAPSHOT_PATH_PREFIX/*", SNAPSHOT_BY_DATE)

            addURI(AUTHORITY, "$SNAPSHOT_MONTH_PATH_PREFIX/latest", SNAPSHOT_MONTH_LATEST)
            addURI(AUTHORITY, "$SNAPSHOT_MONTH_PATH_PREFIX/*", SNAPSHOT_MONTH_BY_MONTH)

            addURI(AUTHORITY, LOGS_PATH, LOGS_RECENT)
            addURI(AUTHORITY, "$GOALS_PATH_PREFIX/current", GOALS_CURRENT)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SharedSnapshotProviderEntryPoint {
        fun buildSharedNutritionSnapshotJsonUseCase(): BuildSharedNutritionSnapshotJsonUseCase
        fun buildSharedNutritionMonthSnapshotJsonUseCase(): BuildSharedNutritionMonthSnapshotJsonUseCase
        fun buildSharedLogExportJsonUseCase(): BuildSharedLogExportJsonUseCase
        fun buildSharedNutritionGoalProfileJsonUseCase(): BuildSharedNutritionGoalProfileJsonUseCase
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null

        MeowLog.d("SharedSnapshotProvider> openFile START uri=$uri")

        try {
            val match = uriMatcher.match(uri)
            val path = uri.path.orEmpty().trimStart('/')

            MeowLog.d(
                "SharedSnapshotProvider> routing " +
                        "authority=${uri.authority} path=$path match=$match"
            )

            val (json, fileName) = when {
                match == SNAPSHOT_LATEST || match == SNAPSHOT_BY_DATE -> {
                    val targetDate = resolveTargetDate(uri)
                    MeowLog.d("SharedSnapshotProvider> building daily snapshot date=$targetDate")

                    val json = buildSnapshotJson(context, targetDate)
                    json to "shared_snapshot_${targetDate}.json"
                }

                match == SNAPSHOT_MONTH_LATEST || match == SNAPSHOT_MONTH_BY_MONTH -> {
                    val targetMonth = resolveTargetMonth(uri)
                    MeowLog.d("SharedSnapshotProvider> building monthly snapshot month=$targetMonth")

                    val json = buildMonthSnapshotJson(context, targetMonth)
                    json to "shared_snapshot_month_${targetMonth}.json"
                }

                match == GOALS_CURRENT || path == "$GOALS_PATH_PREFIX/current" -> {
                    MeowLog.d("SharedSnapshotProvider> building goals snapshot")

                    val json = buildGoalsJson(context)
                    json to "shared_nutrition_goals_current.json"
                }

                match == LOGS_RECENT || path == LOGS_PATH -> {
                    MeowLog.d("SharedSnapshotProvider> building logs snapshot")

                    val json = buildLogsJson(context)
                    json to "shared_logs.json"
                }

                else -> {
                    MeowLog.d("SharedSnapshotProvider> FAIL unknown uri=$uri")
                    throw IllegalArgumentException("Unknown URI: $uri")
                }
            }

            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(json.toByteArray()) }

            MeowLog.d(
                "SharedSnapshotProvider> file written " +
                        "path=${file.absolutePath} size=${file.length()}"
            )

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

            MeowLog.d("SharedSnapshotProvider> SUCCESS uri=$uri file=$fileName")

            return pfd

        } catch (e: Exception) {
            MeowLog.e("SharedSnapshotProvider> FAILED uri=$uri", e)
            throw e
        }
    }

    private fun resolveTargetDate(uri: Uri): LocalDate {
        val result = when (uriMatcher.match(uri)) {
            SNAPSHOT_LATEST -> LocalDate.now()

            SNAPSHOT_BY_DATE -> {
                val lastSegment = uri.lastPathSegment
                    ?: throw IllegalArgumentException("Missing snapshot date in URI: $uri")

                if (lastSegment == "latest") LocalDate.now()
                else LocalDate.parse(lastSegment)
            }

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        MeowLog.d("SharedSnapshotProvider> resolved date=$result")
        return result
    }

    private fun resolveTargetMonth(uri: Uri): YearMonth {
        val result = when (uriMatcher.match(uri)) {
            SNAPSHOT_MONTH_LATEST -> YearMonth.now()

            SNAPSHOT_MONTH_BY_MONTH -> {
                val lastSegment = uri.lastPathSegment
                    ?: throw IllegalArgumentException("Missing snapshot month in URI: $uri")

                if (lastSegment == "latest") YearMonth.now()
                else YearMonth.parse(lastSegment)
            }

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

        MeowLog.d("SharedSnapshotProvider> resolved month=$result")
        return result
    }

    private fun buildSnapshotJson(context: Context, date: LocalDate): String {
        MeowLog.d("SharedSnapshotProvider> buildSnapshotJson START date=$date")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        val result = runBlocking {
            entryPoint
                .buildSharedNutritionSnapshotJsonUseCase()
                .invoke(date = date, zoneId = ZoneId.systemDefault())
        }

        MeowLog.d("SharedSnapshotProvider> buildSnapshotJson SUCCESS length=${result.length}")
        return result
    }

    private fun buildMonthSnapshotJson(context: Context, month: YearMonth): String {
        MeowLog.d("SharedSnapshotProvider> buildMonthSnapshotJson START month=$month")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        val result = runBlocking {
            entryPoint
                .buildSharedNutritionMonthSnapshotJsonUseCase()
                .invoke(month = month, zoneId = ZoneId.systemDefault())
        }

        MeowLog.d("SharedSnapshotProvider> buildMonthSnapshotJson SUCCESS length=${result.length}")
        return result
    }

    private fun buildGoalsJson(context: Context): String {
        MeowLog.d("SharedSnapshotProvider> buildGoalsJson START")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        val result = runBlocking {
            entryPoint
                .buildSharedNutritionGoalProfileJsonUseCase()
                .invoke()
        }

        MeowLog.d("SharedSnapshotProvider> buildGoalsJson SUCCESS length=${result.length}")
        return result
    }

    private fun buildLogsJson(context: Context): String {
        MeowLog.d("SharedSnapshotProvider> buildLogsJson START")

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SharedSnapshotProviderEntryPoint::class.java
        )

        val result = runBlocking {
            entryPoint
                .buildSharedLogExportJsonUseCase()
                .invoke()
        }

        MeowLog.d("SharedSnapshotProvider> buildLogsJson SUCCESS length=${result.length}")
        return result
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val pfd = openFile(uri, mode) ?: return null
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String = "application/json"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}