package com.examtracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ExamStatus(val label: String, val color: Color) {
    REGISTERING("报名中", Color(0xFFEF6C00)),
    UPCOMING("待笔试", Color(0xFF1976D2)),
    FINISHED("已笔试", Color(0xFF388E3C)),
    ENDED("已结束", Color(0xFF757575))
}

fun getExamStatus(regEndTime: Long?, examTime: Long?): ExamStatus {
    val now = System.currentTimeMillis()
    if (regEndTime != null && now <= regEndTime) return ExamStatus.REGISTERING
    if (examTime != null && now <= examTime) {
        val daysUntilExam = TimeUnit.MILLISECONDS.toDays(examTime - now)
        if (daysUntilExam <= 1) return ExamStatus.UPCOMING
        return ExamStatus.UPCOMING
    }
    if (examTime != null && now > examTime) return ExamStatus.FINISHED
    return ExamStatus.ENDED
}

fun getCountdownText(examTime: Long?): String {
    if (examTime == null) return ""
    val now = System.currentTimeMillis()
    val diff = examTime - now
    if (diff <= 0) return "已考完"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days > 0) return "距笔试 $days 天"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours > 0) return "距笔试 $hours 小时"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return "距笔试 $minutes 分钟"
}

@Composable
fun StatusChip(status: ExamStatus) {
    Surface(
        color = status.color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = status.color
        )
    }
}

@Composable
fun CountdownBadge(examTime: Long?) {
    val text = getCountdownText(examTime)
    if (text.isBlank()) return
    val isUrgent = examTime != null && (examTime - System.currentTimeMillis()) <= TimeUnit.DAYS.toMillis(3)
    val bgColor = if (isUrgent) Color(0xFFD32F2F).copy(alpha = 0.12f)
    else Color(0xFF1976D2).copy(alpha = 0.12f)
    val textColor = if (isUrgent) Color(0xFFD32F2F) else Color(0xFF1976D2)
    Surface(color = bgColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private val dateFormatter = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA)
private val fullDateFormatter = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)

fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return ""
    return dateFormatter.format(Date(timestamp))
}

fun formatDateFull(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "未定"
    return fullDateFormatter.format(Date(timestamp))
}
