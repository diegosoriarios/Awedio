package com.diego.awedio.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    suspend fun getTranscriptionById(id: Long): TranscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(entity: TranscriptionEntity): Long

    @Delete
    suspend fun deleteTranscription(entity: TranscriptionEntity)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()
}
