package com.examtracker.ui.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.examtracker.ExamTrackerApp
import com.examtracker.data.SettingsStore
import com.examtracker.data.api.SiliconFlowApi
import com.examtracker.data.api.WebFetcher
import com.examtracker.data.db.ExamEntity
import com.examtracker.data.repository.ExamRepository
import com.examtracker.util.CalendarSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder

data class AddExamState(
    val url: String = "",
    val positionName: String = "",
    val positionCode: String = "",
    val pastedText: String = "",
    val apiKey: String = "",
    val hasSavedKey: Boolean = false,
    val isExtracting: Boolean = false,
    val extractedExam: ExamEntity? = null,
    val error: String = "",
    val showPreview: Boolean = false,
    val savedSuccessfully: Boolean = false,

    // WebView 搜索
    val searchKeyword: String = "",
    val showWebSearch: Boolean = false,
    val webSearchUrl: String = "",
    // 从 WebView 返回后自动切到的 tab（0=链接, 1=粘贴, 2=搜索）
    val activeTab: Int = 2
)

class AddExamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExamRepository by lazy {
        val app = application as ExamTrackerApp
        ExamRepository(app.database.examDao())
    }

    private val context = getApplication<ExamTrackerApp>()

    private val _state = MutableStateFlow(AddExamState())
    val state: StateFlow<AddExamState> = _state

    private var savedApiKey: String = ""

    init {
        viewModelScope.launch {
            savedApiKey = SettingsStore.getApiKeyOnce(context)
            _state.value = _state.value.copy(
                apiKey = savedApiKey,
                hasSavedKey = savedApiKey.isNotBlank()
            )
        }
    }

    fun updateUrl(url: String) { _state.value = _state.value.copy(url = url) }
    fun updatePositionName(name: String) { _state.value = _state.value.copy(positionName = name) }
    fun updatePositionCode(code: String) { _state.value = _state.value.copy(positionCode = code) }
    fun updatePastedText(text: String) { _state.value = _state.value.copy(pastedText = text) }
    fun updateApiKey(key: String) {
        _state.value = _state.value.copy(apiKey = key, hasSavedKey = false)
    }
    fun updateSearchKeyword(keyword: String) {
        _state.value = _state.value.copy(searchKeyword = keyword)
    }

    fun saveApiKeyAndContinue() {
        val key = _state.value.apiKey.trim()
        if (key.isBlank()) return
        viewModelScope.launch {
            SettingsStore.saveApiKey(context, key)
            savedApiKey = key
            _state.value = _state.value.copy(hasSavedKey = true)
        }
    }

    fun editSavedKey() {
        _state.value = _state.value.copy(hasSavedKey = false, apiKey = savedApiKey)
    }

    fun clearSavedKey() {
        viewModelScope.launch {
            SettingsStore.clearApiKey(context)
            savedApiKey = ""
            _state.value = _state.value.copy(apiKey = "", hasSavedKey = false)
        }
    }

    // ===== WebView 搜索 =====

    fun openWebSearch() {
        val keyword = _state.value.searchKeyword.trim()
        if (keyword.isBlank()) {
            _state.value = _state.value.copy(error = "请输入搜索关键字")
            return
        }
        val query = URLEncoder.encode("$keyword 招聘公告", "UTF-8")
        val url = "https://www.bing.com/search?q=$query&setlang=zh-Hans"
        _state.value = _state.value.copy(
            showWebSearch = true,
            webSearchUrl = url,
            error = ""
        )
    }

    fun onWebSearchResult(pageUrl: String) {
        _state.value = _state.value.copy(
            showWebSearch = false,
            url = pageUrl,
            searchKeyword = "",
            activeTab = 0  // 自动切到"公告链接" tab
        )
        startExtraction()
    }

    fun dismissWebSearch() {
        _state.value = _state.value.copy(showWebSearch = false)
    }

    // ===== 提取功能 =====

    fun startExtraction() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.value = s.copy(error = "请先设置硅基流动 API Key")
            return
        }
        if (s.url.isBlank() && s.pastedText.isBlank()) {
            _state.value = s.copy(error = "请输入公告链接或粘贴公告内容")
            return
        }

        _state.value = s.copy(isExtracting = true, error = "", showPreview = false)

        viewModelScope.launch {
            val result = if (s.url.isNotBlank()) {
                val fetchResult = WebFetcher.fetchPage(s.url)
                if (!fetchResult.success) {
                    _state.value = _state.value.copy(
                        isExtracting = false,
                        error = "页面抓取失败: ${fetchResult.error}\n请尝试手动粘贴公告内容"
                    )
                    return@launch
                }
                SiliconFlowApi.extractExamInfo(
                    apiKey = s.apiKey,
                    pageContent = fetchResult.textContent,
                    pageTitle = fetchResult.title,
                    url = s.url
                )
            } else {
                SiliconFlowApi.extractFromPastedText(
                    apiKey = s.apiKey,
                    text = s.pastedText
                )
            }

            if (!result.success || result.exam == null) {
                _state.value = _state.value.copy(
                    isExtracting = false,
                    error = result.error
                )
                return@launch
            }

            val extracted = result.exam
            val examEntity = ExamEntity(
                unitName = extracted.unitName,
                positionName = extracted.positionName.ifBlank { s.positionName },
                positionCode = extracted.positionCode.ifBlank { s.positionCode },
                totalRecruitment = extracted.totalRecruitment,
                orgType = extracted.orgType,
                workLocation = extracted.workLocation,
                announcementUrl = s.url,
                registrationUrl = extracted.registrationUrl,
                account = extracted.account,
                regStartTime = CalendarSync.parseDateString(extracted.regStartTime),
                regEndTime = CalendarSync.parseDateString(extracted.regEndTime),
                reviewEndTime = CalendarSync.parseDateString(extracted.reviewEndTime),
                paymentEndTime = CalendarSync.parseDateString(extracted.paymentEndTime),
                admitCardStart = CalendarSync.parseDateString(extracted.admitCardStart),
                admitCardEnd = CalendarSync.parseDateString(extracted.admitCardEnd),
                examTime = CalendarSync.parseDateString(extracted.examTime),
                examSubjects = extracted.examSubjects,
                examPassLine = extracted.examPassLine,
                scorePublishTime = CalendarSync.parseDateString(extracted.scorePublishTime),
                qualificationReviewTime = CalendarSync.parseDateString(extracted.qualificationReviewTime),
                interviewTime = CalendarSync.parseDateString(extracted.interviewTime),
                interviewFormat = extracted.interviewFormat,
                scoreFormula = extracted.scoreFormula,
                examFee = extracted.examFee,
                notes = extracted.notes
            )

            _state.value = _state.value.copy(
                isExtracting = false,
                extractedExam = examEntity,
                showPreview = true
            )
        }
    }

    fun updateExtractedField(exam: ExamEntity) {
        _state.value = _state.value.copy(extractedExam = exam)
    }

    fun confirmSave() {
        val exam = _state.value.extractedExam ?: return
        _state.value = _state.value.copy(isExtracting = true)

        viewModelScope.launch {
            val savedId = repository.insertExam(exam)
            if (savedId > 0) {
                _state.value = _state.value.copy(
                    isExtracting = false,
                    savedSuccessfully = true,
                    extractedExam = _state.value.extractedExam?.copy(id = savedId)
                )
            } else {
                _state.value = _state.value.copy(
                    isExtracting = false,
                    error = "保存失败"
                )
            }
        }
    }

    fun resetState() {
        _state.value = AddExamState(apiKey = _state.value.apiKey, hasSavedKey = _state.value.hasSavedKey)
    }
}
