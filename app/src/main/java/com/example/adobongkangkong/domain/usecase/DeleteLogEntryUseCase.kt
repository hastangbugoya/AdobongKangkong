package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.repository.LogRepository
import javax.inject.Inject

class DeleteLogEntryUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(logId: Long) {
        logRepository.deleteById(logId)
    }
}