package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.NutriDatabase
import javax.inject.Inject

class HasAnyUserDataUseCase @Inject constructor(
    private val db: NutriDatabase
) {
    suspend operator fun invoke(): Boolean {
        val foods = db.foodDao().countFoods()
        val recipes = db.recipeDao().countRecipes()
        return foods > 0 || recipes > 0
    }
}