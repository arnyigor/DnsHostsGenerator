package com.arny.dnshostsgenerator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: CustomPresetEntity)

    @Query("DELETE FROM custom_presets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM custom_presets ORDER BY title COLLATE NOCASE")
    fun getAll(): Flow<List<CustomPresetEntity>>
}
