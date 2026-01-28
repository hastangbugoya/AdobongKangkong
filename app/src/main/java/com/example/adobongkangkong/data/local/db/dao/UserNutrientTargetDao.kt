package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.UserNutrientTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserNutrientTargetDao {

    @Query("SELECT * FROM user_nutrient_targets")
    fun observeAll(): Flow<List<UserNutrientTargetEntity>>

    @Query("SELECT * FROM user_nutrient_targets WHERE nutrientCode = :code LIMIT 1")
    suspend fun getByCode(code: String): UserNutrientTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserNutrientTargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserNutrientTargetEntity>)

    @Query("DELETE FROM user_nutrient_targets WHERE nutrientCode = :code")
    suspend fun delete(code: String)

    @Query("SELECT EXISTS(SELECT 1 FROM user_nutrient_targets LIMIT 1)")
    suspend fun hasAny(): Boolean
}