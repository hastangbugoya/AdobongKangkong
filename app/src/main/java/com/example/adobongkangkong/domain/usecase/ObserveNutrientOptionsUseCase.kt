package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.ui.dashboard.pinned.model.NutrientOption
import kotlinx.coroutines.flow.Flow

interface ObserveNutrientOptionsUseCase {
    operator fun invoke(): Flow<List<NutrientOption>>
}