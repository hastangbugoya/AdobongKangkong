package com.example.adobongkangkong.domain.backup

import android.content.Context
import android.net.Uri
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
        beforeCopy?.invoke()

        val resolver = context.contentResolver
        val dbFiles = getDbFiles()
        val foodImageRoot = File(filesDirProvider(context), FOOD_IMAGES_DIR)
        val recipeInstructionImageRoot = File(filesDirProvider(context), RECIPE_INSTRUCTION_IMAGES_DIR)

        resolver.openOutputStream(outputUri)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->

                fun addFile(file: File, entryName: String) {
                    if (!file.exists() || !file.isFile) return
                    zip.putNextEntry(ZipEntry(entryName))
                    BufferedInputStream(file.inputStream()).use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }

                fun addDirectoryContents(rootDir: File, zipDirName: String) {
                    if (!rootDir.exists() || !rootDir.isDirectory) return

                    rootDir
                        .walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val rel = file.absolutePath
                                .removePrefix(rootDir.absolutePath + File.separator)
                                .replace("\\", "/")
                            addFile(file, ZIP_FILES_DIR + zipDirName + "/" + rel)
                        }
                }

                // DB files (WAL mode => include wal/shm if present)
                addFile(dbFiles.db, ZIP_DB_DIR + DB_NAME)
                dbFiles.wal?.let { addFile(it, ZIP_DB_DIR + DB_NAME + "-wal") }
                dbFiles.shm?.let { addFile(it, ZIP_DB_DIR + DB_NAME + "-shm") }

                // App-owned media under filesDir
                addDirectoryContents(foodImageRoot, FOOD_IMAGES_DIR)
                addDirectoryContents(recipeInstructionImageRoot, RECIPE_INSTRUCTION_IMAGES_DIR)
            }
        } ?: error("Unable to open output stream for export Uri.")
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
        beforeRestore?.invoke()

        val resolver = context.contentResolver

        val db = databasePathProvider(context, DB_NAME)
        val dbDir = db.parentFile ?: error("Database parent directory missing for $DB_NAME")
        val filesDir = filesDirProvider(context)

        resolver.openInputStream(inputUri)?.use { ins ->
            ZipInputStream(ins.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name

                    when {
                        name.startsWith(ZIP_DB_DIR) -> {
                            val outFile = File(dbDir, name.removePrefix(ZIP_DB_DIR))
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                zip.copyTo(out)
                            }
                        }

                        name.startsWith(ZIP_FILES_DIR) -> {
                            val outFile = File(filesDir, name.removePrefix(ZIP_FILES_DIR))
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                zip.copyTo(out)
                            }
                        }

                        else -> {
                            // ignore unknown entries
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Unable to open input stream for restore Uri.")

        afterRestore?.invoke()
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
        return DbFiles(db = db, wal = wal, shm = shm)
    }
}