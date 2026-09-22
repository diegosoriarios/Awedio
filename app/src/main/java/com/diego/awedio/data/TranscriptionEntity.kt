package com.diego.awedio.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val transcribedText: String,
    val audioFilename: String? = null,
    val durationSeconds: Int? = null
)
