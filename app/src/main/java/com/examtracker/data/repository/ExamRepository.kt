package com.examtracker.data.repository

import com.examtracker.data.db.ExamDao
import com.examtracker.data.db.ExamEntity
import kotlinx.coroutines.flow.Flow

class ExamRepository(private val examDao: ExamDao) {

    val allExams: Flow<List<ExamEntity>> = examDao.getAllExams()
    val upcomingExams: Flow<List<ExamEntity>> = examDao.getUpcomingExams()

    suspend fun getExamById(id: Long): ExamEntity? = examDao.getExamById(id)

    suspend fun insertExam(exam: ExamEntity): Long = examDao.insertExam(exam)

    suspend fun updateExam(exam: ExamEntity) = examDao.updateExam(exam)

    suspend fun deleteExam(exam: ExamEntity) = examDao.deleteExam(exam)

    suspend fun deleteExamById(id: Long) = examDao.deleteExamById(id)

    suspend fun getCount(): Int = examDao.getCount()

    suspend fun seedIfEmpty(seedData: List<ExamEntity>) {
        if (examDao.getCount() == 0) {
            seedData.forEach { examDao.insertExam(it) }
        }
    }
}
