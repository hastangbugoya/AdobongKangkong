package com.example.adobongkangkong.domain.model

import java.time.Instant

data class LogEntry(
    val id: Long = 0,
    val foodId: Long,
    val servings: Double,
    val timestamp: Instant
)