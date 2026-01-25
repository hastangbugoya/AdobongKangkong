package com.example.adobongkangkong.domain.importing.cleanup

interface NutrientColumnResolver {
    fun resolve(columnName: String): Long? // returns nutrientId or null if unknown
}