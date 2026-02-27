package com.example.adobongkangkong.data.backup

import android.net.Uri
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.domain.backup.AppBackupUseCase
import javax.inject.Inject

interface BackupRepository {
    suspend fun exportBackup(outputUri: Uri)
    suspend fun importBackup(inputUri: Uri)
}

class AppBackupRepository @Inject constructor(
    private val useCase: AppBackupUseCase,
    private val db: NutriDatabase
) : BackupRepository {

    override suspend fun exportBackup(outputUri: Uri) {
        useCase.exportToZip(
            outputUri = outputUri,
            beforeCopy = {
                // Best-effort: flush WAL -> main DB so the backup is consistent.
                // Safe even if WAL isn't enabled; it just returns info.
                runCatching {
                    val sqlDb = db.openHelper.writableDatabase
                    sqlDb.query("PRAGMA wal_checkpoint(FULL)").close()
                }
            }
        )
    }

    override suspend fun importBackup(inputUri: Uri) {
        useCase.restoreFromZip(
            inputUri = inputUri,
            beforeRestore = {
                // CRITICAL: Room/SQLite must not hold the DB open while we overwrite files.
                runCatching { db.close() }
            },
            afterRestore = {
                // No-op here; UI should restart process after import.
                // (BackupViewModel already marks needsRestart = true)
            }
        )
    }
}