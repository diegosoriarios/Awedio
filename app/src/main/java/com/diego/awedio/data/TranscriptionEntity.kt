package com.diego.awedio.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TranscriptionStatus {
    QUEUED,
    PROCESSING,
    DONE,
    ERROR
}

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val transcribedText: String,
    val audioFilename: String? = null,
    val durationSeconds: Int? = null,
    @ColumnInfo(defaultValue = "DONE")
    val status: String = TranscriptionStatus.DONE.name,
    val displayName: String? = null
)
