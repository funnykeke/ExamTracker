package com.examtracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomTimelineEventDao {

    @Query("SELECT * FROM custom_timeline_events WHERE examId = :examId ORDER BY timestamp ASC")
    fun getEventsByExamId(examId: Long): Flow<List<CustomTimelineEvent>>

    @Query("SELECT * FROM custom_timeline_events WHERE examId = :examId ORDER BY timestamp ASC")
    suspend fun getEventsByExamIdOnce(examId: Long): List<CustomTimelineEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CustomTimelineEvent): Long

    @Update
    suspend fun updateEvent(event: CustomTimelineEvent)

    @Delete
    suspend fun deleteEvent(event: CustomTimelineEvent)

    @Query("DELETE FROM custom_timeline_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)
}
