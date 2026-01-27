package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.RecipeBatchEntity
import com.example.adobongkangkong.domain.logging.model.BatchSummary

internal fun RecipeBatchEntity.toDomain(): BatchSummary =
    BatchSummary(
        batchId = id,
        recipeId = recipeId,
        cookedYieldGrams = cookedYieldGrams,
        servingsYieldUsed = servingsYieldUsed,
        createdAt = createdAt
    )