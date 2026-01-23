package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit

fun Food.gramsPerServingResolved(): Double? {
    return when (servingUnit) {
        ServingUnit.G -> servingSize
        ServingUnit.ML -> gramsPerServing // later: density
        else -> gramsPerServing
    }
}
