package com.examtracker

import android.app.Application
import com.examtracker.data.db.ExamDatabase
import com.examtracker.data.repository.ExamRepository
import com.examtracker.data.repository.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExamTrackerApp : Application() {

    val database: ExamDatabase by lazy {
        ExamDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: ExamTrackerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Seed initial data on first launch
        CoroutineScope(Dispatchers.IO).launch {
            val repository = ExamRepository(database.examDao())
            repository.seedIfEmpty(SeedData.getSeedExams())
        }
    }
}
