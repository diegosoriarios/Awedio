package com.diego.awedio.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    Flow<List<TranscriptionEntity>> getAllTranscriptions();

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    TranscriptionEntity getTranscriptionById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTranscription(TranscriptionEntity entity);

    @Delete
    void deleteTranscription(TranscriptionEntity entity);

    @Query("DELETE FROM transcriptions")
    void clearAll();
}
