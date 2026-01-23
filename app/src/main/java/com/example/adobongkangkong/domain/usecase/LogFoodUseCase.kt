package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.LogEntry
import com.example.adobongkangkong.domain.repository.LogRepository
import java.time.Instant
import javax.inject.Inject

class LogFoodUseCase @Inject constructor(
    private val repository: LogRepository
) {
    suspend operator fun invoke(
        foodId: Long,
        servings: Double,
        timestamp: Instant = Instant.now()
    ) {
        repository.insert(
            LogEntry(
                foodId = foodId,
                servings = servings,
                timestamp = timestamp
            )
        )
    }
}
