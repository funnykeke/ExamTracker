package com.examtracker.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.examtracker.data.db.ExamEntity
import java.util.Calendar
import java.util.TimeZone

object CalendarSync {

    private const val CALENDAR_NAME = "考试追踪"
    private const val CALENDAR_ACCOUNT = "com.examtracker"
    private const val CALENDAR_COLOR = 0xFF4A90D9.toInt()

    data class CalendarEvent(
        val title: String,
        val description: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val reminders: List<Int> = listOf(60, 1440, 10080) // 1h, 1 day, 7 days before
    )

    fun getOrCreateCalendarId(context: Context): Long {
        val existingId = findCalendarId(context)
        if (existingId != -1L) return existingId

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
        }

        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val newUri = context.contentResolver.insert(uri, values)
        return newUri?.lastPathSegment?.toLongOrNull() ?: -1L
    }

    private fun findCalendarId(context: Context): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME)
        val selection = "${CalendarContract.Calendars.NAME} = ?"
        val args = arrayOf(CALENDAR_NAME)

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return -1L
    }

    fun syncExamToCalendar(context: Context, exam: ExamEntity): List<Long> {
        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return emptyList()

        val eventIds = mutableListOf<Long>()

        // Delete existing events for this exam
        deleteExamEvents(context, exam)

        // Create events for each key date
        val events = buildExamEvents(exam)

        for (event in events) {
            val eventId = createCalendarEvent(context, calendarId, event)
            if (eventId != -1L) eventIds.add(eventId)
        }

        return eventIds
    }

    fun deleteExamEvents(context: Context, exam: ExamEntity) {
        if (exam.calendarEventIds.isBlank()) return

        val ids = exam.calendarEventIds.split(",").mapNotNull { it.toLongOrNull() }
        for (id in ids) {
            deleteSingleEvent(context, id)
        }
    }

    fun deleteSingleEvent(context: Context, calendarEventId: Long) {
        if (calendarEventId <= 0) return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, calendarEventId)
        context.contentResolver.delete(uri, null, null)
    }

    fun syncSingleEvent(
        context: Context,
        exam: ExamEntity,
        title: String,
        description: String,
        icon: String,
        timestamp: Long,
        oldCalendarEventId: Long
    ): Long {
        // delete old calendar event if exists
        if (oldCalendarEventId > 0) {
            deleteSingleEvent(context, oldCalendarEventId)
        }

        val calendarId = getOrCreateCalendarId(context)
        if (calendarId == -1L) return -1L

        val calendarEvent = CalendarEvent(
            title = "$icon $title：${exam.unitName}",
            description = "岗位：${exam.positionName}\n$description",
            startTimeMillis = timestamp,
            endTimeMillis = timestamp + 3600_000,
            reminders = listOf(60, 1440)
        )

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, calendarEvent.title)
            put(CalendarContract.Events.DESCRIPTION, calendarEvent.description)
            put(CalendarContract.Events.DTSTART, calendarEvent.startTimeMillis)
            put(CalendarContract.Events.DTEND, calendarEvent.endTimeMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return -1L

        val eventId = uri.lastPathSegment?.toLongOrNull() ?: -1L

        for (minutes in calendarEvent.reminders) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                put(CalendarContract.Reminders.MINUTES, minutes)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }

        return eventId
    }

    private fun buildExamEvents(exam: ExamEntity): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val unit = exam.unitName
        val pos = exam.positionName

        exam.regEndTime?.let {
            events.add(
                CalendarEvent(
                    title = "📋 报名截止：$unit",
                    description = "岗位：$pos\n岗位代码：${exam.positionCode}\n报名截止时间到了！",
                    startTimeMillis = it,
                    endTimeMillis = it + 3600_000,
                    reminders = listOf(60, 360, 1440)
                )
            )
        }

        exam.paymentEndTime?.let {
            events.add(
                CalendarEvent(
                    title = "💰 缴费截止：$unit",
                    description = "岗位：$pos\n别忘了缴费！",
                    startTimeMillis = it,
                    endTimeMillis = it + 3600_000,
                    reminders = listOf(60, 360, 1440)
                )
            )
        }

        exam.admitCardStart?.let {
            events.add(
                CalendarEvent(
                    title = "🎫 打印准考证：$unit",
                    description = "岗位：$pos\n开始打印准考证",
                    startTimeMillis = it,
                    endTimeMillis = it + 3600_000,
                    reminders = listOf(60)
                )
            )
        }

        exam.examTime?.let {
            events.add(
                CalendarEvent(
                    title = "✏️ 笔试：$unit",
                    description = "岗位：$pos\n岗位代码：${exam.positionCode}\n科目：${exam.examSubjects}",
                    startTimeMillis = it,
                    endTimeMillis = it + 3 * 3600_000,
                    reminders = listOf(60, 1440, 4320, 10080)
                )
            )
        }

        exam.scorePublishTime?.let {
            events.add(
                CalendarEvent(
                    title = "📊 笔试成绩公布：$unit",
                    description = "岗位：$pos\n查看笔试成绩",
                    startTimeMillis = it,
                    endTimeMillis = it + 3600_000,
                    reminders = listOf(60)
                )
            )
        }

        exam.qualificationReviewTime?.let {
            events.add(
                CalendarEvent(
                    title = "📋 资格复审：$unit",
                    description = "岗位：$pos\n带好材料参加资格复审",
                    startTimeMillis = it,
                    endTimeMillis = it + 3600_000,
                    reminders = listOf(60, 1440)
                )
            )
        }

        exam.interviewTime?.let {
            events.add(
                CalendarEvent(
                    title = "🎤 面试：$unit",
                    description = "岗位：$pos\n形式：${exam.interviewFormat}",
                    startTimeMillis = it,
                    endTimeMillis = it + 4 * 3600_000,
                    reminders = listOf(60, 1440, 4320, 10080)
                )
            )
        }

        return events
    }

    private fun createCalendarEvent(
        context: Context,
        calendarId: Long,
        event: CalendarEvent
    ): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.DTSTART, event.startTimeMillis)
            put(CalendarContract.Events.DTEND, event.endTimeMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

        val uri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            values
        ) ?: return -1L

        val eventId = uri.lastPathSegment?.toLongOrNull() ?: -1L

        // Add reminders
        for (minutes in event.reminders) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                put(CalendarContract.Reminders.MINUTES, minutes)
            }
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                reminderValues
            )
        }

        return eventId
    }

    fun parseDateString(dateStr: String): Long? {
        if (dateStr.isBlank()) return null

        val patterns = listOf(
            Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})\s+(\d{1,2}):(\d{2})"""),
            Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})"""),
            Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})"""),
            Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""")
        )

        for (pattern in patterns) {
            val match = pattern.find(dateStr) ?: continue
            val groups = match.groupValues
            val year = groups[1].toInt()
            val month = groups[2].toInt() - 1
            val day = groups[3].toInt()
            val hour = groups.getOrNull(4)?.toInt() ?: 9
            val minute = groups.getOrNull(5)?.toInt() ?: 0

            return Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        return null
    }
}
