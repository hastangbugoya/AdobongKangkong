package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit

fun Food.gramsPerServingUnitResolved(): Double? {
    return when (servingUnit) {
        ServingUnit.G -> servingSize
        ServingUnit.ML -> gramsPerServingUnit // later: density
        else -> gramsPerServingUnit
    }
}

