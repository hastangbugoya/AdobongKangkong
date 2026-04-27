package com.example.adobongkangkong.domain.backup

import android.content.Context
import android.net.Uri
import com.example.adobongkangkong.core.log.MeowLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * AppBackupUseCase
 *
 * Exports and restores a complete migration bundle.
 *
 * Included:
 * - Room DB files: nutri.db (+ -wal / -shm if present)
 * - Banner masters directory: filesDir/food_images (all files inside)
 * - Recipe instruction step images: filesDir/recipe_instruction_images (all files inside)
 *
 * Excluded (cache / derivable):
 * - cacheDir (blur images regenerate automatically)
 *
 * CRITICAL:
 * - Restore must be performed when the Room DB is CLOSED.
 * - After restore, restart the app process so Room reopens the restored DB cleanly.
 *
 * Testability note:
 * - The Hilt constructor remains production-only and requires only Context.
 * - A secondary constructor exists for tests so backup I/O can be redirected to isolated paths
 *   without changing production DI wiring.
 */
class AppBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val DB_NAME = "nutri.db"

        // Zip layout (inside archive)
        private const val ZIP_DB_DIR = "databases/"
        private const val ZIP_FILES_DIR = "files/"

        // Authoritative media we include
        private const val FOOD_IMAGES_DIR = "food_images"
        private const val RECIPE_INSTRUCTION_IMAGES_DIR = "recipe_instruction_images"
    }

    private var databasePathProvider: (Context, String) -> File = { ctx, dbName ->
        ctx.getDatabasePath(dbName)
    }

    private var filesDirProvider: (Context) -> File = { ctx ->
        ctx.filesDir
    }

    internal constructor(
        context: Context,
        databasePathProvider: (Context, String) -> File,
        filesDirProvider: (Context) -> File
    ) : this(context) {
        this.databasePathProvider = databasePathProvider
        this.filesDirProvider = filesDirProvider
    }

    /**
     * Creates a ZIP at [outputUri] containing:
     * - databases/nutri.db, nutri.db-wal, nutri.db-shm (if present)
     * - files/food_images (all files inside)
     * - files/recipe_instruction_images (all files inside)
     *
     * Optional [beforeCopy] hook lets UI do a WAL checkpoint / pause writers if desired.
     */
    suspend fun exportToZip(
        outputUri: Uri,
        beforeCopy: (suspend () -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        MeowLog.d("AppBackupUseCase> exportToZip START uri=$outputUri")

        try {
            if (beforeCopy != null) {
                MeowLog.d("AppBackupUseCase> exportToZip beforeCopy START")
                beforeCopy.invoke()
                MeowLog.d("AppBackupUseCase> exportToZip beforeCopy SUCCESS")
            } else {
                MeowLog.d("AppBackupUseCase> exportToZip beforeCopy skipped")
            }

            val resolver = context.contentResolver
            val dbFiles = getDbFiles()
            val foodImageRoot = File(filesDirProvider(context), FOOD_IMAGES_DIR)
            val recipeInstructionImageRoot = File(filesDirProvider(context), RECIPE_INSTRUCTION_IMAGES_DIR)

            MeowLog.d(
                "AppBackupUseCase> exportToZip paths " +
                        "db=${dbFiles.db.absolutePath} exists=${dbFiles.db.exists()} " +
                        "wal=${dbFiles.wal?.absolutePath} " +
                        "shm=${dbFiles.shm?.absolutePath} " +
                        "foodImages=${foodImageRoot.absolutePath} exists=${foodImageRoot.exists()} " +
                        "recipeImages=${recipeInstructionImageRoot.absolutePath} exists=${recipeInstructionImageRoot.exists()}"
            )

            var dbEntryCount = 0
            var mediaEntryCount = 0

            resolver.openOutputStream(outputUri)?.use { os ->
                MeowLog.d("AppBackupUseCase> exportToZip output stream opened")

                ZipOutputStream(os.buffered()).use { zip ->

                    fun addFile(file: File, entryName: String): Boolean {
                        if (!file.exists() || !file.isFile) {
                            MeowLog.d("AppBackupUseCase> exportToZip skip missing file entry=$entryName path=${file.absolutePath}")
                            return false
                        }

                        MeowLog.d(
                            "AppBackupUseCase> exportToZip addFile START " +
                                    "entry=$entryName size=${file.length()}"
                        )

                        zip.putNextEntry(ZipEntry(entryName))
                        BufferedInputStream(file.inputStream()).use { input ->
                            input.copyTo(zip)
                        }
                        zip.closeEntry()

                        MeowLog.d("AppBackupUseCase> exportToZip addFile SUCCESS entry=$entryName")
                        return true
                    }

                    fun addDirectoryContents(rootDir: File, zipDirName: String): Int {
                        if (!rootDir.exists() || !rootDir.isDirectory) {
                            MeowLog.d(
                                "AppBackupUseCase> exportToZip skip directory " +
                                        "zipDir=$zipDirName path=${rootDir.absolutePath}"
                            )
                            return 0
                        }

                        MeowLog.d(
                            "AppBackupUseCase> exportToZip addDirectory START " +
                                    "zipDir=$zipDirName path=${rootDir.absolutePath}"
                        )

                        var count = 0
                        rootDir
                            .walkTopDown()
                            .filter { it.isFile }
                            .forEach { file ->
                                val rel = file.absolutePath
                                    .removePrefix(rootDir.absolutePath + File.separator)
                                    .replace("\\", "/")
                                val added = addFile(file, ZIP_FILES_DIR + zipDirName + "/" + rel)
                                if (added) count += 1
                            }

                        MeowLog.d(
                            "AppBackupUseCase> exportToZip addDirectory SUCCESS " +
                                    "zipDir=$zipDirName count=$count"
                        )

                        return count
                    }

                    // DB files (WAL mode => include wal/shm if present)
                    if (addFile(dbFiles.db, ZIP_DB_DIR + DB_NAME)) dbEntryCount += 1
                    dbFiles.wal?.let {
                        if (addFile(it, ZIP_DB_DIR + DB_NAME + "-wal")) dbEntryCount += 1
                    }
                    dbFiles.shm?.let {
                        if (addFile(it, ZIP_DB_DIR + DB_NAME + "-shm")) dbEntryCount += 1
                    }

                    MeowLog.d("AppBackupUseCase> exportToZip DB section SUCCESS count=$dbEntryCount")

                    // App-owned media under filesDir
                    mediaEntryCount += addDirectoryContents(foodImageRoot, FOOD_IMAGES_DIR)
                    mediaEntryCount += addDirectoryContents(recipeInstructionImageRoot, RECIPE_INSTRUCTION_IMAGES_DIR)

                    MeowLog.d("AppBackupUseCase> exportToZip media section SUCCESS count=$mediaEntryCount")
                }
            } ?: error("Unable to open output stream for export Uri.")

            MeowLog.d(
                "AppBackupUseCase> exportToZip SUCCESS " +
                        "dbEntries=$dbEntryCount mediaEntries=$mediaEntryCount uri=$outputUri"
            )
        } catch (t: Throwable) {
            MeowLog.e("AppBackupUseCase> exportToZip FAILED uri=$outputUri", t)
            throw t
        }
    }

    /**
     * Restores a ZIP previously created by [exportToZip].
     *
     * Overwrites:
     * - databases/nutri.db (+ -wal / -shm if present in zip)
     * - filesDir/food_images (all files inside)
     * - filesDir/recipe_instruction_images (all files inside)
     *
     * Optional [beforeRestore] should close Room / stop writers.
     * Optional [afterRestore] is a good place to restart process.
     */
    suspend fun restoreFromZip(
        inputUri: Uri,
        beforeRestore: (suspend () -> Unit)? = null,
        afterRestore: (suspend () -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        MeowLog.d("AppBackupUseCase> restoreFromZip START uri=$inputUri")

        try {
            if (beforeRestore != null) {
                MeowLog.d("AppBackupUseCase> restoreFromZip beforeRestore START")
                beforeRestore.invoke()
                MeowLog.d("AppBackupUseCase> restoreFromZip beforeRestore SUCCESS")
            } else {
                MeowLog.d("AppBackupUseCase> restoreFromZip beforeRestore skipped")
            }

            val resolver = context.contentResolver

            val db = databasePathProvider(context, DB_NAME)
            val dbDir = db.parentFile ?: error("Database parent directory missing for $DB_NAME")
            val filesDir = filesDirProvider(context)

            MeowLog.d(
                "AppBackupUseCase> restoreFromZip paths " +
                        "dbDir=${dbDir.absolutePath} filesDir=${filesDir.absolutePath}"
            )

            var dbEntryCount = 0
            var fileEntryCount = 0
            var ignoredEntryCount = 0

            resolver.openInputStream(inputUri)?.use { ins ->
                MeowLog.d("AppBackupUseCase> restoreFromZip input stream opened")

                ZipInputStream(ins.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name

                        when {
                            name.startsWith(ZIP_DB_DIR) -> {
                                val outFile = File(dbDir, name.removePrefix(ZIP_DB_DIR))

                                MeowLog.d(
                                    "AppBackupUseCase> restoreFromZip restore DB entry START " +
                                            "entry=$name out=${outFile.absolutePath}"
                                )

                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    zip.copyTo(out)
                                }

                                dbEntryCount += 1
                                MeowLog.d("AppBackupUseCase> restoreFromZip restore DB entry SUCCESS entry=$name")
                            }

                            name.startsWith(ZIP_FILES_DIR) -> {
                                val outFile = File(filesDir, name.removePrefix(ZIP_FILES_DIR))

                                MeowLog.d(
                                    "AppBackupUseCase> restoreFromZip restore file entry START " +
                                            "entry=$name out=${outFile.absolutePath}"
                                )

                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    zip.copyTo(out)
                                }

                                fileEntryCount += 1
                                MeowLog.d("AppBackupUseCase> restoreFromZip restore file entry SUCCESS entry=$name")
                            }

                            else -> {
                                ignoredEntryCount += 1
                                MeowLog.d("AppBackupUseCase> restoreFromZip ignore unknown entry=$name")
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Unable to open input stream for restore Uri.")

            MeowLog.d(
                "AppBackupUseCase> restoreFromZip copy SUCCESS " +
                        "dbEntries=$dbEntryCount fileEntries=$fileEntryCount ignoredEntries=$ignoredEntryCount"
            )

            if (afterRestore != null) {
                MeowLog.d("AppBackupUseCase> restoreFromZip afterRestore START")
                afterRestore.invoke()
                MeowLog.d("AppBackupUseCase> restoreFromZip afterRestore SUCCESS")
            } else {
                MeowLog.d("AppBackupUseCase> restoreFromZip afterRestore skipped")
            }

            MeowLog.d(
                "AppBackupUseCase> restoreFromZip SUCCESS " +
                        "dbEntries=$dbEntryCount fileEntries=$fileEntryCount ignoredEntries=$ignoredEntryCount uri=$inputUri"
            )
        } catch (t: Throwable) {
            MeowLog.e("AppBackupUseCase> restoreFromZip FAILED uri=$inputUri", t)
            throw t
        }
    }

    private data class DbFiles(
        val db: File,
        val wal: File?,
        val shm: File?
    )

    private fun getDbFiles(): DbFiles {
        val db = databasePathProvider(context, DB_NAME)
        val wal = File(db.parentFile, DB_NAME + "-wal").takeIf { it.exists() && it.isFile }
        val shm = File(db.parentFile, DB_NAME + "-shm").takeIf { it.exists() && it.isFile }

        MeowLog.d(
            "AppBackupUseCase> getDbFiles " +
                    "db=${db.absolutePath} exists=${db.exists()} size=${if (db.exists()) db.length() else 0} " +
                    "walExists=${wal != null} shmExists=${shm != null}"
        )

        return DbFiles(db = db, wal = wal, shm = shm)
    }
}