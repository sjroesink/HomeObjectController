package com.homeobjectcontroller.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomLabelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: CustomLabel): Long

    @Query("SELECT * FROM custom_labels WHERE mlKitCategory = :category")
    suspend fun getAllByCategory(category: String): List<CustomLabel>

    @Query("SELECT * FROM custom_labels")
    suspend fun getAll(): List<CustomLabel>

    @Query("DELETE FROM custom_labels WHERE id = :id")
    suspend fun deleteById(id: Long)
}
