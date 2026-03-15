package com.example.adobongkangkong.domain.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * AppBackupUseCaseInstrumentedTest
 *
 * Instrumented regression tests for [AppBackupUseCase] using:
 * - a real on-disk Room database
 * - real file I/O
 * - real ZIP export/restore
 *
 * Why instrumented tests:
 * - AppBackupUseCase depends on Android Context, ContentResolver, database paths, filesDir,
 *   and URI stream access
 * - the main requirement is real round-trip correctness, which is better verified with actual
 *   app-like storage behavior than with mocked JVM-only tests
 *
 * Fully verified in this suite:
 * - export creates a ZIP containing the database file and any present WAL/SHM sidecars
 * - export includes files from filesDir/food_images when that directory exists
 * - export safely omits food_images entries when filesDir/food_images does not exist
 * - restore returns DB contents to the exported snapshot after later DB mutations
 * - restore returns backed-up media file contents after later media mutation
 * - unknown ZIP entries are ignored safely
 *
 * Current-behavior documentation intentionally preserved:
 * - restore is additive for filesDir/food_images
 * - files added after export at new food_images paths are not removed by restore
 *
 * Important invariant:
 * - restore is performed only while Room is closed
 * - DB is reopened only after restore completes
 */
@RunWith(AndroidJUnit4::class)
class AppBackupUseCaseInstrumentedTest {
    /*******************************************************************************************
     *
     * ⚠⚠⚠  WARNING — DEVICE-MODIFYING INSTRUMENTED TEST  ⚠⚠⚠
     *
     * This test runs as an Android INSTRUMENTED TEST (`androidTest`).
     *
     * Running instrumented tests may cause Android Studio / Gradle to:
     *
     *   • REINSTALL the application package
     *   • WIPE the application's data directory
     *   • REMOVE the currently installed app instance on the device
     *
     * This behavior is part of the Android test deployment process and NOT caused by
     * AppBackupUseCase itself.
     *
     * DO NOT run this test on a device containing important app data.
     *
     * Recommended environments:
     *
     *   ✔ Android emulator
     *   ✔ Dedicated test device
     *   ✔ Temporary debug install
     *
     * Avoid running on:
     *
     *   ✘ Your primary personal phone with real data
     *
     * -----------------------------------------------------------------------------------------
     * Safe workflow:
     *
     *   1. Run normal JVM unit tests with:
     *        ./gradlew test
     *
     *   2. Run instrumented backup tests ONLY when needed with:
     *        ./gradlew connectedAndroidTest
     *
     * -----------------------------------------------------------------------------------------
     *
     * If you accidentally triggered this test:
     *
     *   • reinstall the app from Android Studio
     *   • restore your in-app backup if needed
     *
     *******************************************************************************************/
    private lateinit var appContext: Context
    private lateinit var testRootDir: File
    private lateinit var testContext: BackupTestContext
    private lateinit var useCase: AppBackupUseCase

    private var db: NutriDatabase? = null

    @Before
    fun refusePhysicalDevice() {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()

        val isEmulator =
            "generic" in fingerprint ||
                    "emulator" in fingerprint ||
                    "sdk_gphone" in product ||
                    "sdk" == product ||
                    "emulator" in model ||
                    "android sdk built for x86" in model ||
                    "genymotion" in manufacturer

        check(isEmulator) {
            """
        REFUSING TO RUN AppBackupUseCaseInstrumentedTest ON A PHYSICAL DEVICE.

        This is an androidTest and Android deployment may uninstall or wipe the target app.
        Run this test only on an emulator or dedicated throwaway test device.
        """.trimIndent()
        }
    }

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()

        testRootDir = File(
            appContext.cacheDir,
            "backup_test_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        ).apply { mkdirs() }

        testContext = BackupTestContext(
            base = appContext,
            rootDir = testRootDir
        )

        useCase = AppBackupUseCase(testContext)
        db = openDb()
    }

    @After
    fun tearDown() {
        db?.close()
        db = null
        testRootDir.deleteRecursively()
    }

    @Test
    fun exportToZip_includesExpectedDatabaseEntries_andFoodImages() = runBlocking {
        val database = requireDb()

        val foodId = database.foodDao().insert(
            FoodEntity(
                name = "Export Test Food",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )
        assertTrue(foodId > 0L)

        writeFoodImage(
            relativePath = "banners/export-test.txt",
            content = "image-one"
        )
        writeFoodImage(
            relativePath = "nested/deeper/export-two.txt",
            content = "image-two"
        )

        val exportUri = newZipUri("export_includes_expected.zip")
        useCase.exportToZip(exportUri)

        ZipFile(fileForUri(exportUri)).use { zipFile ->
            val entryNames = zipFile.entries().asSequence().map { it.name }.toSet()

            assertTrue(
                "Expected primary DB entry in zip",
                "databases/nutri.db" in entryNames
            )

            val dbPath = testContext.getDatabasePath("nutri.db")
            val walFile = File(dbPath.parentFile, "nutri.db-wal")
            val shmFile = File(dbPath.parentFile, "nutri.db-shm")

            if (walFile.exists() && walFile.isFile) {
                assertTrue(
                    "Expected WAL entry because WAL file exists",
                    "databases/nutri.db-wal" in entryNames
                )
            }

            if (shmFile.exists() && shmFile.isFile) {
                assertTrue(
                    "Expected SHM entry because SHM file exists",
                    "databases/nutri.db-shm" in entryNames
                )
            }

            assertTrue(
                "Expected food image entry in zip",
                "files/food_images/banners/export-test.txt" in entryNames
            )
            assertTrue(
                "Expected nested food image entry in zip",
                "files/food_images/nested/deeper/export-two.txt" in entryNames
            )
        }
    }

    @Test
    fun exportToZip_whenFoodImagesDirDoesNotExist_omitsFoodImageEntries() = runBlocking {
        val foodImagesDir = File(testContext.filesDir, "food_images")
        if (foodImagesDir.exists()) {
            foodImagesDir.deleteRecursively()
        }
        assertFalse(foodImagesDir.exists())

        val database = requireDb()
        database.foodDao().insert(
            FoodEntity(
                name = "No Images Food",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        val exportUri = newZipUri("export_without_images_dir.zip")
        useCase.exportToZip(exportUri)

        ZipFile(fileForUri(exportUri)).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toList()

            assertTrue(
                "Expected DB entry in zip",
                entryNames.contains("databases/nutri.db")
            )

            assertFalse(
                "Did not expect any files/food_images entries when folder is absent",
                entryNames.any { it.startsWith("files/food_images/") }
            )
        }
    }

    @Test
    fun roundTripRestore_restoresDatabaseSnapshot_andRestoresBackedUpMedia_andRemovesPostExportDbChanges() = runBlocking {
        val database = requireDb()

        val originalFoodId = database.foodDao().insert(
            FoodEntity(
                name = "Snapshot Food",
                brand = "Before Export",
                servingSize = 2.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        val originalFood = requireNotNull(database.foodDao().getById(originalFoodId))

        writeFoodImage(
            relativePath = "banners/hero.txt",
            content = "before-export-media"
        )

        val exportUri = newZipUri("round_trip.zip")
        useCase.exportToZip(exportUri)

        database.foodDao().insert(
            FoodEntity(
                name = "Added After Export",
                brand = "Should Disappear",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        overwriteFoodImage(
            relativePath = "banners/hero.txt",
            content = "after-export-media-change"
        )

        closeDb()
        useCase.restoreFromZip(exportUri)

        db = openDb()
        val restoredDb = requireDb()

        val allFoods = restoredDb.foodDao().getAll().sortedBy { it.id }
        assertEquals(
            "Expected DB to return to pre-export snapshot row count",
            1,
            allFoods.size
        )

        val restoredFood = requireNotNull(restoredDb.foodDao().getById(originalFoodId))
        assertEquals(originalFood, restoredFood)

        val restoredMedia = readFoodImage("banners/hero.txt")
        assertEquals(
            "Expected media content to return to exported snapshot",
            "before-export-media",
            restoredMedia
        )
    }

    @Test
    fun restoreFromZip_ignoresUnknownEntriesSafely() = runBlocking {
        val database = requireDb()

        val originalFoodId = database.foodDao().insert(
            FoodEntity(
                name = "Known Good Food",
                brand = "Snapshot",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        writeFoodImage(
            relativePath = "banners/known.txt",
            content = "known-media"
        )

        val cleanExportUri = newZipUri("clean_export.zip")
        useCase.exportToZip(cleanExportUri)

        val unknownZipUri = newZipUri("unknown_entries.zip")
        rewriteZipAddingUnknownEntries(
            sourceZip = fileForUri(cleanExportUri),
            targetZip = fileForUri(unknownZipUri)
        )

        database.foodDao().insert(
            FoodEntity(
                name = "Mutated After Export",
                brand = "Should Disappear",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        overwriteFoodImage(
            relativePath = "banners/known.txt",
            content = "changed-after-export"
        )

        closeDb()
        useCase.restoreFromZip(unknownZipUri)

        db = openDb()
        val restoredDb = requireDb()

        val foods = restoredDb.foodDao().getAll()
        assertEquals(1, foods.size)
        assertNotNull(restoredDb.foodDao().getById(originalFoodId))
        assertEquals("known-media", readFoodImage("banners/known.txt"))

        assertFalse(
            "Unknown top-level output should not be materialized under test root",
            File(testRootDir, "unknown").exists()
        )
        assertFalse(
            "Unknown top-level output should not be materialized under filesDir",
            File(testContext.filesDir, "unknown").exists()
        )
    }

    @Test
    fun restoreFromZip_currentBehavior_keepsPostExportFoodImagesAtNewPaths_additiveRestore() = runBlocking {
        val database = requireDb()

        database.foodDao().insert(
            FoodEntity(
                name = "Media Behavior Food",
                servingSize = 1.0,
                servingUnit = ServingUnit.SERVING
            )
        )

        writeFoodImage(
            relativePath = "snapshots/original.txt",
            content = "original-media"
        )

        val exportUri = newZipUri("media_additive_behavior.zip")
        useCase.exportToZip(exportUri)

        writeFoodImage(
            relativePath = "snapshots/added-after-export.txt",
            content = "new-media-after-export"
        )

        closeDb()
        useCase.restoreFromZip(exportUri)

        db = openDb()

        assertEquals(
            "Expected original backed-up media to exist after restore",
            "original-media",
            readFoodImage("snapshots/original.txt")
        )

        assertTrue(
            "Current behavior: restore does not clear files/food_images first, so new files remain",
            foodImageFile("snapshots/added-after-export.txt").exists()
        )
        assertEquals(
            "Current behavior: post-export media at a new path remains after restore",
            "new-media-after-export",
            readFoodImage("snapshots/added-after-export.txt")
        )
    }

    private fun openDb(): NutriDatabase {
        return Room.databaseBuilder(
            testContext,
            NutriDatabase::class.java,
            "nutri.db"
        )
            .allowMainThreadQueries()
            .build()
    }

    private fun requireDb(): NutriDatabase = requireNotNull(db)

    private fun closeDb() {
        db?.close()
        db = null
    }

    private fun newZipUri(fileName: String): Uri {
        val file = File(testRootDir, fileName)
        file.parentFile?.mkdirs()
        return Uri.fromFile(file)
    }

    private fun fileForUri(uri: Uri): File {
        return requireNotNull(uri.path) { "Uri path was null: $uri" }
            .let(::File)
    }

    private fun foodImageFile(relativePath: String): File {
        return File(File(testContext.filesDir, "food_images"), relativePath)
    }

    private fun writeFoodImage(relativePath: String, content: String) {
        val file = foodImageFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun overwriteFoodImage(relativePath: String, content: String) {
        val file = foodImageFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun readFoodImage(relativePath: String): String {
        return foodImageFile(relativePath).readText()
    }

    private fun rewriteZipAddingUnknownEntries(
        sourceZip: File,
        targetZip: File
    ) {
        ZipFile(sourceZip).use { zipIn ->
            targetZip.outputStream().use { fos ->
                ZipOutputStream(fos.buffered()).use { zipOut ->
                    zipIn.entries().asSequence().forEach { entry ->
                        zipOut.putNextEntry(ZipEntry(entry.name))
                        zipIn.getInputStream(entry).use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }

                    zipOut.putNextEntry(ZipEntry("unknown/ignored.txt"))
                    zipOut.write("ignore me".toByteArray())
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("totally-unrelated.bin"))
                    zipOut.write(byteArrayOf(1, 2, 3, 4))
                    zipOut.closeEntry()
                }
            }
        }
    }

    /**
     * Test context wrapper that redirects database/files/cache paths into an isolated test root.
     *
     * Returning itself from applicationContext is intentional so Room and file APIs resolve
     * through these overridden paths instead of the host app's normal storage.
     */
    private class BackupTestContext(
        base: Context,
        private val rootDir: File
    ) : ContextWrapper(base) {

        private val dbDir = File(rootDir, "databases").apply { mkdirs() }
        private val filesRoot = File(rootDir, "files").apply { mkdirs() }
        private val cacheRoot = File(rootDir, "cache").apply { mkdirs() }

        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File {
            dbDir.mkdirs()
            return File(dbDir, name)
        }

        override fun getFilesDir(): File {
            filesRoot.mkdirs()
            return filesRoot
        }

        override fun getCacheDir(): File {
            cacheRoot.mkdirs()
            return cacheRoot
        }
    }
}

/**
 * -----------------------------------------------------------------------------
 * Bottom KDoc for future AI assistant / future dev
 * -----------------------------------------------------------------------------
 *
 * Status:
 * - This suite intentionally targets AppBackupUseCase behavior as it exists today.
 * - Do not "clean up" current-behavior assertions unless production behavior itself changes.
 *
 * Verified now:
 * - ZIP export includes DB and present food_images files
 * - restore round-trip resets DB state to exported snapshot
 * - restore round-trip restores overwritten backed-up media files
 * - unknown entries are ignored
 * - missing food_images directory during export is handled
 *
 * Important current behavior that is intentionally documented:
 * - restore is additive for filesDir/food_images
 * - new media files created after export at previously absent paths remain after restore
 *
 * Future improvements to add when appropriate:
 * 1. WAL-focused regression
 *    - explicitly force/verify WAL sidecar presence during export
 *    - confirm restored DB snapshot remains consistent when WAL existed at export time
 *
 * 2. SHM/WAL stale-sidecar behavior
 *    - document current behavior when backup ZIP omits wal/shm but old local sidecars exist
 *    - if production later deletes old wal/shm before restore, add assertions for that
 *
 * 3. beforeCopy / beforeRestore / afterRestore callback behavior
 *    - verify hooks are invoked exactly once
 *    - verify ordering relative to export/restore operations
 *
 * 4. binary media verification
 *    - add byte-for-byte assertions using non-text content
 *    - current suite uses text payloads for readability and simpler failure messages
 *
 * 5. richer DB snapshot coverage
 *    - add a second table when needed
 *    - keep scope narrow unless backup scope expands or a bug specifically requires broader coverage
 *
 * 6. corrupt / truncated ZIP behavior
 *    - add explicit failure-path tests if production requirements tighten around error handling
 *
 * Do not remove future plans from this KDoc just because they are not implemented yet.
 * They are here to preserve intent and prevent re-discovery work in later sessions.
 */