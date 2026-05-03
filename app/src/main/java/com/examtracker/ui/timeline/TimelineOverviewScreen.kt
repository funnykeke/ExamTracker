package com.examtracker.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineOverviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: TimelineOverviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val now = remember { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("全局时间线", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "所有考试 · ${state.events.size} 个事项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.showPast,
                        onClick = { viewModel.toggleShowPast() },
                        label = { Text("含已过", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { Text("加载中…") }
            return@Scaffold
        }

        if (state.events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.showPast) "暂无任何时间节点" else "没有未来的日程\n可点击「含已过」查看历史",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            return@Scaffold
        }

        // Pre-compute month/year strings and isNext flag outside composables to reduce allocations
        val annotatedEvents = remember(state.events) {
            val list = state.events
            list.mapIndexed { index, event ->
                val monthStr = monthFmt.format(Date(event.timestamp))
                val yearStr = yearFmt.format(Date(event.timestamp))
                val prevMonthStr = if (index > 0) monthFmt.format(Date(list[index - 1].timestamp)) else ""
                val prevYearStr = if (index > 0) yearFmt.format(Date(list[index - 1].timestamp)) else ""
                val showMonthHeader = index == 0 || monthStr != prevMonthStr || yearStr != prevYearStr
                val isNext = !event.isPast && (index == 0 || list[index - 1].isPast)
                val dayStr = dayFmt.format(Date(event.timestamp))
                val timeStr = timeFmt.format(Date(event.timestamp))
                AnnotatedEvent(
                    event = event,
                    yearStr = yearStr,
                    monthStr = monthStr,
                    dayStr = dayStr,
                    timeStr = timeStr,
                    showMonthHeader = showMonthHeader,
                    isNext = isNext,
                    isLast = index == list.size - 1
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "top_spacer") { Spacer(modifier = Modifier.height(12.dp)) }

            itemsIndexed(
                items = annotatedEvents,
                key = { _, item -> "${item.event.examId}_${item.event.eventTitle}_${item.event.timestamp}" }
            ) { _, item ->
                if (item.showMonthHeader) {
                    MonthHeader(year = item.yearStr, month = item.monthStr)
                }
                if (item.isNext) {
                    NextUpBanner()
                }
                TimelineRow(
                    item = item,
                    now = now,
                    onClick = { onNavigateToDetail(item.event.examId) }
                )
            }

            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// Pre-computed annotation to avoid repeated calculations in composable
private data class AnnotatedEvent(
    val event: GlobalTimelineEvent,
    val yearStr: String,
    val monthStr: String,
    val dayStr: String,
    val timeStr: String,
    val showMonthHeader: Boolean,
    val isNext: Boolean,
    val isLast: Boolean
)

// Cached formatters (thread-safe for read only from main thread)
private val monthFmt = SimpleDateFormat("MM月", Locale.CHINA)
private val dayFmt = SimpleDateFormat("dd日\nEEE", Locale.CHINA)
private val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
private val yearFmt = SimpleDateFormat("yyyy", Locale.CHINA)

@Composable
private fun MonthHeader(year: String, month: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$year $month",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun NextUpBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "▶ 最近待办",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TimelineRow(
    item: AnnotatedEvent,
    now: Long,
    onClick: () -> Unit
) {
    val event = item.event
    val dotColor = when {
        event.isPast -> Color(0xFFBDBDBD)
        item.isNext -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        // Date column — use pre-computed strings
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(52.dp)
        ) {
            Text(
                text = item.dayStr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (event.isPast)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = if (event.isPast)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timeline dot + line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (item.isNext) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
                contentAlignment = Alignment.Center
            ) {
                if (item.isNext) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
            if (!item.isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(52.dp)
                        .background(Color(0xFFE0E0E0))
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Content card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (item.isLast) 0.dp else 4.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (item.isNext) 3.dp else if (event.isPast) 0.dp else 1.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    item.isNext -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    event.isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = event.eventIcon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.eventTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (event.isPast) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (event.isPast)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = event.unitName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (event.isPast)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary
                    )
                    if (event.positionName.isNotBlank()) {
                        Text(
                            text = event.positionName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // Countdown badge for upcoming
                if (!event.isPast) {
                    val diff = event.timestamp - now
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    val badge = when {
                        days == 0L -> "今天"
                        days == 1L -> "明天"
                        days <= 7L -> "${days}天后"
                        else -> null
                    }
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (days <= 1L) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.tertiary,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
