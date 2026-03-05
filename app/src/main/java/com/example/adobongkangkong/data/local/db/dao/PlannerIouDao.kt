package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.PlannerIouEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerIouDao {

    @Query(
        """
        SELECT *
        FROM planner_ious
        WHERE dateIso = :dateIso
        ORDER BY createdAtEpochMs ASC, id ASC
        """
    )
    fun observeForDate(dateIso: String): Flow<List<PlannerIouEntity>>

    @Query(
        """
        SELECT *
        FROM planner_ious
        WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso
        ORDER BY dateIso ASC, createdAtEpochMs ASC, id ASC
        """
    )
    fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<PlannerIouEntity>>

    @Query("SELECT * FROM planner_ious WHERE id = :id")
    suspend fun getById(id: Long): PlannerIouEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlannerIouEntity): Long

    @Update
    suspend fun update(entity: PlannerIouEntity)

    @Query("DELETE FROM planner_ious WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM planner_ious WHERE dateIso = :dateIso")
    suspend fun deleteForDate(dateIso: String)
}
