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

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC, id ASC")
    Flow<List<TranscriptionEntity>> getAllTranscriptions();

    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC, id ASC")
    List<TranscriptionEntity> getTranscriptionsList();

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    TranscriptionEntity getTranscriptionById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTranscription(TranscriptionEntity entity);

    @Delete
    void deleteTranscription(TranscriptionEntity entity);

    @Query("DELETE FROM transcriptions")
    void clearAll();

    @Query("UPDATE transcriptions SET status = :status WHERE id = :id")
    void updateStatus(long id, String status);

    @Query("UPDATE transcriptions SET status = :status, transcribedText = :text, audioFilename = :audioFilename, durationSeconds = :duration WHERE id = :id")
    void completeTranscription(long id, String status, String text, String audioFilename, Integer duration);

    @Query("UPDATE transcriptions SET status = 'ERROR' WHERE status IN ('QUEUED', 'PROCESSING')")
    int failStaleQueueItems();
}
