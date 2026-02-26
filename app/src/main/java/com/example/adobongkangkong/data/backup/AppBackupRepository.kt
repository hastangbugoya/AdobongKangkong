package com.example.adobongkangkong.data.backup

import android.net.Uri
import com.example.adobongkangkong.domain.backup.AppBackupUseCase
import javax.inject.Inject

interface BackupRepository {
    suspend fun exportBackup(outputUri: Uri)
    suspend fun importBackup(inputUri: Uri)
}

class AppBackupRepository @Inject constructor(
    private val useCase: AppBackupUseCase
) : BackupRepository {

    override suspend fun exportBackup(outputUri: Uri) {
        // Optional: pass a beforeCopy hook later if you wire DB checkpointing.
        useCase.exportToZip(outputUri = outputUri, beforeCopy = null)
    }

    override suspend fun importBackup(inputUri: Uri) {
        // IMPORTANT: restore must happen when Room is CLOSED.
        // Optional: wire beforeRestore/afterRestore hooks later if desired.
        useCase.restoreFromZip(
            inputUri = inputUri,
            beforeRestore = null,
            afterRestore = null
        )
    }
}