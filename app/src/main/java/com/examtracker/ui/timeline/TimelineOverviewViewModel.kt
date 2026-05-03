package com.examtracker.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examtracker.ExamTrackerApp
import com.examtracker.data.db.ExamEntity
import com.examtracker.data.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GlobalTimelineEvent(
    val examId: Long,
    val unitName: String,
    val positionName: String,
    val eventTitle: String,
    val eventIcon: String,
    val timestamp: Long,
    val isPast: Boolean
)

data class TimelineOverviewState(
    val events: List<GlobalTimelineEvent> = emptyList(),
    val isLoading: Boolean = true,
    val showPast: Boolean = false
)

class TimelineOverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExamRepository by lazy {
        val app = application as ExamTrackerApp
        ExamRepository(app.database.examDao())
    }

    private val _state = MutableStateFlow(TimelineOverviewState())
    val state: StateFlow<TimelineOverviewState> = _state

    // Cache all exams so we can re-filter without re-querying
    private var cachedExams: List<ExamEntity> = emptyList()

    init {
        viewModelScope.launch {
            repository.allExams.collect { exams ->
                cachedExams = exams
                applyFilter()
            }
        }
    }

    fun toggleShowPast() {
        _state.value = _state.value.copy(showPast = !_state.value.showPast)
        applyFilter()
    }

    private fun applyFilter() {
        val now = System.currentTimeMillis()
        val showPast = _state.value.showPast
        val events = mutableListOf<GlobalTimelineEvent>()

        for (exam in cachedExams) {
            fun addEvent(title: String, icon: String, ts: Long?) {
                ts ?: return
                val isPast = ts < now
                if (!showPast && isPast) return
                events.add(
                    GlobalTimelineEvent(
                        examId = exam.id,
                        unitName = exam.unitName,
                        positionName = exam.positionName,
                        eventTitle = title,
                        eventIcon = icon,
                        timestamp = ts,
                        isPast = isPast
                    )
                )
            }
            addEvent("报名开始", "📝", exam.regStartTime)
            addEvent("报名截止", "🚫", exam.regEndTime)
            addEvent("资格初审截止", "🔍", exam.reviewEndTime)
            addEvent("缴费截止", "💰", exam.paymentEndTime)
            addEvent("准考证打印", "🎫", exam.admitCardStart)
            addEvent("笔试", "✏️", exam.examTime)
            addEvent("成绩公布", "📊", exam.scorePublishTime)
            addEvent("资格复审", "📋", exam.qualificationReviewTime)
            addEvent("面试", "🎤", exam.interviewTime)
        }

        _state.value = _state.value.copy(
            events = events.sortedBy { it.timestamp },
            isLoading = false
        )
    }
}
