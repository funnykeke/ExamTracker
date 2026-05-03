package com.examtracker.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examtracker.data.db.ExamEntity
import com.examtracker.ui.components.ExamStatus
import com.examtracker.ui.components.getCountdownText
import com.examtracker.ui.components.getExamStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TableColumn(
    val title: String,
    val width: Dp,
    val align: TextAlign = TextAlign.Start,
    val extractor: (ExamEntity, Int) -> String
)

private val dateFmt = SimpleDateFormat("MM/dd", Locale.CHINA)
private val fullDateFmt = SimpleDateFormat("yyyy/MM/dd\nHH:mm", Locale.CHINA)

private fun fmtDate(ts: Long?): String {
    if (ts == null || ts == 0L) return "-"
    return dateFmt.format(Date(ts))
}

private fun fmtFullDate(ts: Long?): String {
    if (ts == null || ts == 0L) return "-"
    return fullDateFmt.format(Date(ts))
}

private val columns = listOf(
    TableColumn("#", 36.dp, TextAlign.Center) { _, i -> "${i + 1}" },
    TableColumn("招聘单位", 130.dp) { e, _ -> e.unitName },
    TableColumn("岗位", 100.dp) { e, _ -> e.positionName },
    TableColumn("代码", 68.dp, TextAlign.Center) { e, _ -> e.positionCode },
    TableColumn("报名截止", 80.dp, TextAlign.Center) { e, _ -> fmtDate(e.regEndTime) },
    TableColumn("缴费截止", 80.dp, TextAlign.Center) { e, _ -> fmtDate(e.paymentEndTime) },
    TableColumn("准考证", 80.dp, TextAlign.Center) { e, _ -> fmtDate(e.admitCardStart) },
    TableColumn("笔试时间", 90.dp, TextAlign.Center) { e, _ -> fmtFullDate(e.examTime) },
    TableColumn("笔试科目", 140.dp) { e, _ -> e.examSubjects },
    TableColumn("合格线", 60.dp, TextAlign.Center) { e, _ -> e.examPassLine },
    TableColumn("成绩公布", 80.dp, TextAlign.Center) { e, _ -> fmtDate(e.scorePublishTime) },
    TableColumn("资格复审", 80.dp, TextAlign.Center) { e, _ -> fmtDate(e.qualificationReviewTime) },
    TableColumn("面试时间", 90.dp, TextAlign.Center) { e, _ -> fmtFullDate(e.interviewTime) },
    TableColumn("状态", 64.dp, TextAlign.Center) { e, _ ->
        when (getExamStatus(e.regEndTime, e.examTime)) {
            ExamStatus.REGISTERING -> "报名中"
            ExamStatus.UPCOMING -> getCountdownText(e.examTime)
            ExamStatus.FINISHED -> "已笔试"
            ExamStatus.ENDED -> "已结束"
        }
    },
    TableColumn("备注", 120.dp) { e, _ -> e.notes }
)

val totalWidth = columns.sumOf { it.width.value.toInt() }.dp + 16.dp * (columns.size - 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableScreen(
    onNavigateBack: () -> Unit,
    onExamClick: (Long) -> Unit,
    viewModel: TableViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考试总览表") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.exams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无考试数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            return@Scaffold
        }

        val sortedExams = state.exams.sortedBy { it.examTime ?: Long.MAX_VALUE }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(ExamStatus.REGISTERING)
                Spacer(Modifier.width(4.dp))
                Text("报名中", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
                LegendDot(ExamStatus.UPCOMING)
                Spacer(Modifier.width(4.dp))
                Text("待笔试", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
                LegendDot(ExamStatus.FINISHED)
                Spacer(Modifier.width(4.dp))
                Text("已笔试", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(4.dp))

            // Table
            val scrollState = rememberScrollState()

            // Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            columns.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .width(col.width)
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    contentAlignment = when (col.align) {
                                        TextAlign.Center -> Alignment.Center
                                        TextAlign.End -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    }
                                ) {
                                    Text(
                                        text = col.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        textAlign = col.align,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Data rows
                    itemsIndexed(sortedExams, key = { _, exam -> exam.id }) { index, exam ->
                        val status = getExamStatus(exam.regEndTime, exam.examTime)
                        val rowColor = if (index % 2 == 0)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

                        Row(
                            modifier = Modifier
                                .horizontalScroll(scrollState)
                                .background(rowColor)
                                .clickable { onExamClick(exam.id) }
                        ) {
                            columns.forEach { col ->
                                val value = col.extractor(exam, index)
                                TableDataCell(
                                    value = value,
                                    width = col.width,
                                    align = col.align,
                                    status = if (col.title == "状态") status else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(status: ExamStatus) {
    Box(
        modifier = Modifier
            .width(10.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(status.color)
    )
}

@Composable
private fun TableDataCell(
    value: String,
    width: Dp,
    align: TextAlign,
    status: ExamStatus? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = when (align) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        if (status != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = status.color.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = status.color,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else {
            val isHighlight = align != TextAlign.Start && value != "-" && value.length > 3
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal
                ),
                color = when {
                    value == "-" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    isHighlight -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}
