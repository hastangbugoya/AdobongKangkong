package com.example.adobongkangkong.domain.trend.model

data class ComplianceScore(
    val nutrientCode: String,
    val days: Int,
    val okDays: Int,
    val percentOk: Double
)
