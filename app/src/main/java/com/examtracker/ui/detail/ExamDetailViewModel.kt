package com.examtracker.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examtracker.ExamTrackerApp
import com.examtracker.data.db.CustomTimelineEvent
import com.examtracker.data.db.EventType
import com.examtracker.data.db.ExamEntity
import com.examtracker.data.repository.ExamRepository
import com.examtracker.util.CalendarSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ExamDetailState(
    val exam: ExamEntity? = null,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val syncMessage: String = "",
    val customEvents: List<CustomTimelineEvent> = emptyList(),
    val customEventSyncingId: Long = 0  // 正在同步的自定义事件 ID，0 表示无
)

class ExamDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ExamTrackerApp

    private val repository: ExamRepository by lazy {
        ExamRepository(app.database.examDao())
    }

    private val eventDao = app.database.customTimelineEventDao()

    private val _state = MutableStateFlow(ExamDetailState())
    val state: StateFlow<ExamDetailState> = _state

    private var currentExamId: Long = 0
    private var eventsJob: Job? = null

    fun loadExam(id: Long) {
        currentExamId = id
        viewModelScope.launch {
            val exam = repository.getExamById(id)
            _state.value = _state.value.copy(exam = exam, isLoading = false)
        }
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            eventDao.getEventsByExamId(id).collectLatest { events ->
                _state.value = _state.value.copy(customEvents = events)
            }
        }
    }

    fun setMessage(msg: String) {
        _state.value = _state.value.copy(syncMessage = msg)
    }

    fun syncToCalendar() {
        val exam = _state.value.exam ?: return
        _state.value = _state.value.copy(isSyncing = true, syncMessage = "")

        viewModelScope.launch {
            try {
                val context = getApplication<ExamTrackerApp>()
                val eventIds = CalendarSync.syncExamToCalendar(context, exam)

                if (eventIds.isNotEmpty()) {
                    val updatedExam = exam.copy(
                        calendarEventIds = eventIds.joinToString(",")
                    )
                    repository.updateExam(updatedExam)
                    _state.value = _state.value.copy(
                        exam = updatedExam,
                        isSyncing = false,
                        syncMessage = "已同步 ${eventIds.size} 个事件到日历 ✓"
                    )
                } else {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        syncMessage = "没有需要同步的时间节点"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSyncing = false,
                    syncMessage = "同步失败: ${e.message}"
                )
            }
        }
    }

    // ── 自定义时间点 CRUD ──────────────────────────────────────────

    fun addCustomEvent(title: String, icon: String, timestamp: Long, eventType: String = "CUSTOM") {
        if (currentExamId == 0L || title.isBlank() || timestamp == 0L) return
        viewModelScope.launch {
            eventDao.insertEvent(
                CustomTimelineEvent(
                    examId = currentExamId,
                    title = title.trim(),
                    icon = icon,
                    timestamp = timestamp,
                    eventType = eventType
                )
            )
            // 如果是标准类型，同步更新 ExamEntity 对应字段
            syncExamFieldFromEvent(currentExamId, eventType, timestamp)
        }
    }

    fun updateCustomEvent(event: CustomTimelineEvent) {
        viewModelScope.launch {
            eventDao.updateEvent(event)
            // 如果是标准类型，同步更新 ExamEntity 对应字段
            syncExamFieldFromEvent(event.examId, event.eventType, event.timestamp)
        }
    }

    private suspend fun syncExamFieldFromEvent(examId: Long, eventType: String, timestamp: Long) {
        val type = EventType.fromString(eventType)
        if (type == EventType.CUSTOM) return
        val exam = repository.getExamById(examId) ?: return
        val updated = when (type) {
            EventType.REGISTRATION_START -> exam.copy(regStartTime = timestamp)
            EventType.REGISTRATION_END -> exam.copy(regEndTime = timestamp)
            EventType.REVIEW_END -> exam.copy(reviewEndTime = timestamp)
            EventType.PAYMENT_END -> exam.copy(paymentEndTime = timestamp)
            EventType.ADMIT_CARD -> exam.copy(admitCardStart = timestamp)
            EventType.EXAM -> exam.copy(examTime = timestamp)
            EventType.SCORE_PUBLISH -> exam.copy(scorePublishTime = timestamp)
            EventType.QUALIFICATION_REVIEW -> exam.copy(qualificationReviewTime = timestamp)
            EventType.INTERVIEW -> exam.copy(interviewTime = timestamp)
            else -> return
        }
        repository.updateExam(updated)
        _state.value = _state.value.copy(exam = updated)
    }

    fun deleteCustomEvent(event: CustomTimelineEvent) {
        viewModelScope.launch {
            // 删除关联的日历事件
            if (event.calendarEventId > 0) {
                try {
                    CalendarSync.deleteSingleEvent(app, event.calendarEventId)
                } catch (_: Exception) {}
            }
            eventDao.deleteEvent(event)
        }
    }

    fun syncCustomEvent(event: CustomTimelineEvent) {
        val exam = _state.value.exam ?: return
        _state.value = _state.value.copy(customEventSyncingId = event.id)

        viewModelScope.launch {
            try {
                val newCalendarId = CalendarSync.syncSingleEvent(
                    context = app,
                    exam = exam,
                    title = event.title,
                    description = "",
                    icon = event.icon,
                    timestamp = event.timestamp,
                    oldCalendarEventId = event.calendarEventId
                )
                if (newCalendarId > 0) {
                    eventDao.updateEvent(event.copy(calendarEventId = newCalendarId))
                    _state.value = _state.value.copy(
                        customEventSyncingId = 0,
                        syncMessage = "「${event.title}」已同步到日历 ✓"
                    )
                } else {
                    _state.value = _state.value.copy(
                        customEventSyncingId = 0,
                        syncMessage = "同步失败：无法创建日历事件"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    customEventSyncingId = 0,
                    syncMessage = "同步失败: ${e.message}"
                )
            }
        }
    }

    // ── 报名信息 ────────────────────────────────────────────────────

    fun saveAccountInfo(
        examId: Long,
        account: String,
        password: String,
        registeredPositionName: String,
        registeredPositionCode: String
    ) {
        viewModelScope.launch {
            val exam = repository.getExamById(examId) ?: return@launch
            val updated = exam.copy(
                account = account.trim(),
                accountPassword = password.trim(),
                registeredPositionName = registeredPositionName.trim(),
                registeredPositionCode = registeredPositionCode.trim()
            )
            repository.updateExam(updated)
            _state.value = _state.value.copy(exam = updated)
        }
    }

    // ── 缴费状态 ────────────────────────────────────────────────────

    fun toggleIsPaid(examId: Long, isPaid: Boolean) {
        viewModelScope.launch {
            val exam = repository.getExamById(examId) ?: return@launch
            val updated = exam.copy(isPaid = isPaid)
            repository.updateExam(updated)
            _state.value = _state.value.copy(exam = updated)
        }
    }

    // ── 删除 ────────────────────────────────────────────────────────

    fun deleteExam(id: Long) {
        viewModelScope.launch {
            val context = getApplication<ExamTrackerApp>()
            // delete standard calendar events
            _state.value.exam?.let { exam ->
                try {
                    CalendarSync.deleteExamEvents(context, exam)
                } catch (_: Exception) {}
            }
            // delete custom event calendar entries
            try {
                val customEvents = eventDao.getEventsByExamIdOnce(id)
                for (ce in customEvents) {
                    if (ce.calendarEventId > 0) {
                        try { CalendarSync.deleteSingleEvent(context, ce.calendarEventId) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            repository.deleteExamById(id)
        }
    }
}
