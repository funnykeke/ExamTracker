package com.examtracker.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examtracker.ExamTrackerApp
import com.examtracker.data.db.ExamEntity
import com.examtracker.data.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExamListState(
    val exams: List<ExamEntity> = emptyList(),
    val isLoading: Boolean = true
)

class ExamListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExamRepository by lazy {
        val app = application as ExamTrackerApp
        ExamRepository(app.database.examDao())
    }

    private val _state = MutableStateFlow(ExamListState())
    val state: StateFlow<ExamListState> = _state

    init {
        viewModelScope.launch {
            repository.allExams.collect { exams ->
                _state.value = ExamListState(
                    exams = exams,
                    isLoading = false
                )
            }
        }
    }

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            repository.deleteExam(exam)
        }
    }
}
