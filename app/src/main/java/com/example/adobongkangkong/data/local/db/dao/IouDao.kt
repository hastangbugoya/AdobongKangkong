package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.IouEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IouDao {

    @Query(
        """
        SELECT *
        FROM ious
        WHERE dateIso = :dateIso
        ORDER BY createdAtEpochMs ASC, id ASC
        """
    )
    fun observeForDate(dateIso: String): Flow<List<IouEntity>>

    @Query(
        """
        SELECT *
        FROM ious
        WHERE dateIso >= :startDateIso AND dateIso <= :endDateIso
        ORDER BY dateIso ASC, createdAtEpochMs ASC, id ASC
        """
    )
    fun observeInRange(startDateIso: String, endDateIso: String): Flow<List<IouEntity>>

    @Query("SELECT * FROM ious WHERE id = :id")
    suspend fun getById(id: Long): IouEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: IouEntity): Long

    @Update
    suspend fun update(entity: IouEntity)

    @Query("DELETE FROM ious WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ious WHERE dateIso = :dateIso")
    suspend fun deleteForDate(dateIso: String)
}
