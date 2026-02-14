package com.example.adobongkangkong.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.adobongkangkong.domain.usecase.CleanupOrphanFoodMediaUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OrphanFoodMediaCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val cleanup: CleanupOrphanFoodMediaUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            cleanup()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
