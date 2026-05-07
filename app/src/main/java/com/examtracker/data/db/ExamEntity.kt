package com.examtracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitName: String = "",
    val positionName: String = "",
    val positionCode: String = "",
    val totalRecruitment: String = "",
    val orgType: String = "",
    val workLocation: String = "",
    val announcementUrl: String = "",
    val registrationUrl: String = "",
    val account: String = "",
    val accountPassword: String = "",
    val registeredPositionName: String = "",
    val registeredPositionCode: String = "",
    val regStartTime: Long? = null,
    val regEndTime: Long? = null,
    val reviewEndTime: Long? = null,
    val paymentEndTime: Long? = null,
    val admitCardStart: Long? = null,
    val admitCardEnd: Long? = null,
    val examTime: Long? = null,
    val examSubjects: String = "",
    val examPassLine: String = "",
    val scorePublishTime: Long? = null,
    val qualificationReviewTime: Long? = null,
    val interviewTime: Long? = null,
    val interviewFormat: String = "",
    val scoreFormula: String = "",
    val examFee: String = "",
    val isPaid: Boolean = false,
    val status: String = "",
    val notes: String = "",
    val calendarEventIds: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
