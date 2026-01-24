package com.example.adobongkangkong.domain.repository

import kotlinx.coroutines.flow.Flow

interface NutrientAliasRepository {
    fun observeAliases(nutrientId: Long): Flow<List<String>>
    suspend fun replaceAliases(nutrientId: Long, aliases: List<String>)
    suspend fun addAlias(nutrientId: Long, alias: String)
    suspend fun deleteAlias(nutrientId: Long, alias: String)
}
