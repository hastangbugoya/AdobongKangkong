package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.PinnedGroceryItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AK's small "Always on list" grocery add-on.
 *
 * This intentionally manages only persistent manual staples. Planner-generated
 * grocery output should remain derived elsewhere and should not be inserted here.
 */
@Dao
interface PinnedGroceryItemDao {

    @Query(
        """
        SELECT *
        FROM pinned_grocery_items
        ORDER BY isChecked ASC, sortOrder ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<PinnedGroceryItemEntity>>

    @Query(
        """
        SELECT *
        FROM pinned_grocery_items
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getById(id: Long): PinnedGroceryItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PinnedGroceryItemEntity): Long

    @Update
    suspend fun update(entity: PinnedGroceryItemEntity)

    @Delete
    suspend fun delete(entity: PinnedGroceryItemEntity)

    @Query(
        """
        UPDATE pinned_grocery_items
        SET isChecked = :isChecked,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun setChecked(
        id: Long,
        isChecked: Boolean,
        updatedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE pinned_grocery_items
        SET name = :name,
            quantityText = :quantityText,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """
    )
    suspend fun updateText(
        id: Long,
        name: String,
        quantityText: String?,
        updatedAtEpochMs: Long
    )

    @Query("DELETE FROM pinned_grocery_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
