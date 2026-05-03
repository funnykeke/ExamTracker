package com.examtracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_timeline_events",
    foreignKeys = [ForeignKey(
        entity = ExamEntity::class,
        parentColumns = ["id"],
        childColumns = ["examId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("examId")]
)
data class CustomTimelineEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val title: String,
    val icon: String = "📌",
    val timestamp: Long,
    val calendarEventId: Long = 0
)
