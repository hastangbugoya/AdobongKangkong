package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food

fun servingsFromServingUnits(
    unitAmount: Double,
    servingSize: Double
): Double = unitAmount / servingSize

fun servingsFromGrams(
    grams: Double,
    gramsPerServingUnit: Double
): Double = grams / gramsPerServingUnit

fun servingUnitsFromServings(
    servings: Double,
    servingSize: Double
): Double = servings * servingSize

fun gramsFromServings(
    servings: Double,
    gramsPerServingUnit: Double
): Double = servings * gramsPerServingUnit

