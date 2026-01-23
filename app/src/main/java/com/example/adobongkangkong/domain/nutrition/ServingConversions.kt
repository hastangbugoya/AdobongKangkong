package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food

fun servingsFromServingUnits(
    unitAmount: Double,
    servingSize: Double
): Double = unitAmount / servingSize

fun servingsFromGrams(
    grams: Double,
    gramsPerServing: Double
): Double = grams / gramsPerServing

fun servingUnitsFromServings(
    servings: Double,
    servingSize: Double
): Double = servings * servingSize

fun gramsFromServings(
    servings: Double,
    gramsPerServing: Double
): Double = servings * gramsPerServing

