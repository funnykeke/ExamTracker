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
import com.examtracker.data.db.ExamEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ExamStatus(val label: String, val color: Color) {
    REGISTERING("报名中", Color(0xFFEF6C00)),
    UPCOMING("待笔试", Color(0xFF1976D2)),
    FINISHED("已笔试", Color(0xFF388E3C)),
    ENDED("已结束", Color(0xFF757575))
}

fun hasRegistrationInfo(account: String, registeredPositionName: String): Boolean =
    account.isNotBlank() || registeredPositionName.isNotBlank()

fun getExamStatus(
    regEndTime: Long?,
    examTime: Long?,
    hasRegistered: Boolean = false
): ExamStatus {
    val now = System.currentTimeMillis()
    if (!hasRegistered && regEndTime != null && now <= regEndTime) return ExamStatus.REGISTERING
    if (examTime != null && now <= examTime) return ExamStatus.UPCOMING
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

fun getRegistrationCountdownText(regEndTime: Long?): String {
    if (regEndTime == null) return ""
    val now = System.currentTimeMillis()
    val diff = regEndTime - now
    if (diff <= 0) return ""
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days > 0) return "距报名结束${days}天"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours > 0) return "距报名结束${hours}小时"
    return "距报名结束${TimeUnit.MILLISECONDS.toMinutes(diff)}分钟"
}

fun getPaymentCountdownText(paymentEndTime: Long?): String {
    if (paymentEndTime == null) return ""
    val now = System.currentTimeMillis()
    val diff = paymentEndTime - now
    if (diff <= 0) return "缴费已截止"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days > 0) return "距缴费结束剩${days}天"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours > 0) return "距缴费结束剩${hours}小时"
    return "距缴费结束剩${TimeUnit.MILLISECONDS.toMinutes(diff)}分钟"
}

fun getPaymentUrgencyColor(paymentEndTime: Long?): Color {
    if (paymentEndTime == null) return Color(0xFFFFA726)
    val now = System.currentTimeMillis()
    val daysLeft = TimeUnit.MILLISECONDS.toDays(paymentEndTime - now)
    return when {
        daysLeft < 0  -> Color(0xFFB71C1C)  // overdue: dark red
        daysLeft <= 1 -> Color(0xFFD32F2F)  // ≤1 day: red
        daysLeft <= 3 -> Color(0xFFEF5350)  // 1-3 days
        daysLeft <= 7 -> Color(0xFFFF7043)  // 3-7 days
        daysLeft <= 14 -> Color(0xFFFFA726) // 7-14 days
        else           -> Color(0xFFFFCA28) // >14 days: amber
    }
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

@Composable
fun PaymentStatusBadge(exam: ExamEntity) {
    if (!hasRegistrationInfo(exam.account, exam.registeredPositionName)) return

    if (exam.isPaid) {
        Surface(
            color = Color(0xFF388E3C).copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "已缴费",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF388E3C)
            )
        }
    } else {
        val text = getPaymentCountdownText(exam.paymentEndTime)
        val color = getPaymentUrgencyColor(exam.paymentEndTime)
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = text.ifBlank { "未缴费" },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun RegistrationCountdownBadge(
    regEndTime: Long?,
    account: String,
    registeredPositionName: String
) {
    if (hasRegistrationInfo(account, registeredPositionName)) return
    val text = getRegistrationCountdownText(regEndTime)
    if (text.isBlank()) return

    val diff = (regEndTime ?: return) - System.currentTimeMillis()
    val isUrgent = diff <= TimeUnit.DAYS.toMillis(3)
    val color = if (isUrgent) Color(0xFFD32F2F) else Color(0xFFEF6C00)

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")
    .withZone(ZoneId.systemDefault())

fun formatDateFull(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "未定"
    return fullDateFormatter.format(Instant.ofEpochMilli(timestamp))
}
