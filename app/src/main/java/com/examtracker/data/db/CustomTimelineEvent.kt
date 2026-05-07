package com.examtracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EventType(val label: String, val icon: String, val examField: String) {
    CUSTOM("自定义", "📌", ""),
    REGISTRATION_START("报名开始", "📝", "regStartTime"),
    REGISTRATION_END("报名截止", "📋", "regEndTime"),
    REVIEW_END("初审截止", "🔍", "reviewEndTime"),
    PAYMENT_END("缴费截止", "💰", "paymentEndTime"),
    ADMIT_CARD("打印准考证", "🎫", "admitCardStart"),
    EXAM("笔试", "✏️", "examTime"),
    SCORE_PUBLISH("成绩公布", "📊", "scorePublishTime"),
    QUALIFICATION_REVIEW("资格复审", "📋", "qualificationReviewTime"),
    INTERVIEW("面试", "🎤", "interviewTime");

    companion object {
        fun fromString(value: String): EventType =
            entries.find { it.name == value } ?: CUSTOM
    }
}

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
    val eventType: String = "CUSTOM",
    val calendarEventId: Long = 0
)
