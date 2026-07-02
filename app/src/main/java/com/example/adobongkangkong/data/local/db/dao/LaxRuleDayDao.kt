package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.LaxRuleDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LaxRuleDayDao {

    @Query(
        """
        SELECT *
        FROM lax_rule_days
        WHERE dateEpochDay = :dateEpochDay
        LIMIT 1
        """
    )
    fun observeForDate(dateEpochDay: Long): Flow<LaxRuleDayEntity?>

    @Query(
        """
        SELECT dateEpochDay
        FROM lax_rule_days
        WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY dateEpochDay ASC
        """
    )
    fun observeMarkedDatesBetween(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<Long>>

    @Query(
        """
        SELECT *
        FROM lax_rule_days
        WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY dateEpochDay ASC
        """
    )
    fun observeEntitiesBetween(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<LaxRuleDayEntity>>

    @Query(
        """
        SELECT COUNT(*)
        FROM lax_rule_days
        WHERE dateEpochDay BETWEEN :weekStartEpochDay AND :weekEndEpochDay
        """
    )
    suspend fun countMarkedDaysInWeek(
        weekStartEpochDay: Long,
        weekEndEpochDay: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LaxRuleDayEntity)

    @Query(
        """
        DELETE FROM lax_rule_days
        WHERE dateEpochDay = :dateEpochDay
        """
    )
    suspend fun deleteForDate(dateEpochDay: Long)
}
