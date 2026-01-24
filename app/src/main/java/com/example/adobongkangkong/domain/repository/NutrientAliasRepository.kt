package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.domain.model.AliasAddResult
import kotlinx.coroutines.flow.Flow

interface NutrientAliasRepository {
    fun observeAliases(nutrientId: Long): Flow<List<String>>
    suspend fun replaceAliases(nutrientId: Long, aliases: List<String>)
    suspend fun addAlias(nutrientId: Long, alias: String) : AliasAddResult
    suspend fun deleteAlias(nutrientId: Long, alias: String)
}
