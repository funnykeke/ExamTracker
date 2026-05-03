package com.examtracker.ui.table

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examtracker.ExamTrackerApp
import com.examtracker.data.db.ExamEntity
import com.examtracker.data.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TableState(
    val exams: List<ExamEntity> = emptyList(),
    val isLoading: Boolean = true
)

class TableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExamRepository by lazy {
        val app = application as ExamTrackerApp
        ExamRepository(app.database.examDao())
    }

    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state

    init {
        viewModelScope.launch {
            repository.allExams.collect { exams ->
                _state.value = TableState(exams = exams, isLoading = false)
            }
        }
    }
}
