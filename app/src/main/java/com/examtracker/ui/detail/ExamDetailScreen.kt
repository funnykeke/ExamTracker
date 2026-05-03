package com.examtracker.ui.detail

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examtracker.data.db.CustomTimelineEvent
import com.examtracker.data.db.ExamEntity
import com.examtracker.ui.components.StatusChip
import com.examtracker.ui.components.TimelineItem
import com.examtracker.ui.components.TimelineView
import com.examtracker.ui.components.formatDateFull
import com.examtracker.ui.components.getExamStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val iconOptions = listOf("📝", "🔍", "💰", "🎫", "✏️", "📊", "📋", "🎤", "📌", "🏫", "📅", "⏰", "📢", "✅", "📎", "🔔")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamDetailScreen(
    examId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ExamDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // edit mode for account info
    var isEditingAccount by remember { mutableStateOf(false) }
    var editAccount by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }
    var editRegPosName by remember { mutableStateOf("") }
    var editRegPosCode by remember { mutableStateOf("") }

    // custom event dialog
    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CustomTimelineEvent?>(null) }
    var eventTitle by remember { mutableStateOf("") }
    var eventIcon by remember { mutableStateOf("📌") }
    var eventTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // helper functions
    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
    }

    fun openInBrowser(url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    // calendar permission launcher
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_CALENDAR] == true &&
                permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) viewModel.syncToCalendar()
        else viewModel.setMessage("日历权限被拒绝")
    }

    fun requestCalendarPermissionAndSync() {
        val readOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (readOk && writeOk) viewModel.syncToCalendar()
        else calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    LaunchedEffect(examId) { viewModel.loadExam(examId) }

    LaunchedEffect(state.syncMessage) {
        if (state.syncMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(state.syncMessage)
        }
    }

    // ── Custom event dialog ─────────────────────────────────────────
    if (showEventDialog) {
        // date picker
        if (showDatePicker) {
            val dateState = rememberDatePickerState(initialSelectedDateMillis = eventTimestamp)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            val cal = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
                            val newCal = Calendar.getInstance().apply { timeInMillis = ms }
                            cal.set(Calendar.YEAR, newCal.get(Calendar.YEAR))
                            cal.set(Calendar.MONTH, newCal.get(Calendar.MONTH))
                            cal.set(Calendar.DAY_OF_MONTH, newCal.get(Calendar.DAY_OF_MONTH))
                            eventTimestamp = cal.timeInMillis
                        }
                        showDatePicker = false
                        showTimePicker = true
                    }) { Text("下一步") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
            ) { DatePicker(state = dateState) }
        }

        // time picker
        if (showTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = eventTimestamp }
            val timeState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text("选择时间") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                        cal.set(Calendar.MINUTE, timeState.minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        eventTimestamp = cal.timeInMillis
                        showTimePicker = false
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } }
            )
        }

        // edit dialog
        AlertDialog(
            onDismissRequest = {
                showEventDialog = false
                editingEvent = null
            },
            title = { Text(if (editingEvent != null) "编辑时间点" else "添加时间点") },
            text = {
                Column {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("名称") },
                        placeholder = { Text("如：面试材料审核") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // date + time display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateFull(eventTimestamp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("修改", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("选择图标", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconOptions.forEach { icon ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (eventIcon == icon) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .clickable { eventIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = icon, fontSize = 20.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank() && eventTimestamp > 0) {
                            val e = editingEvent
                            if (e != null) {
                                viewModel.updateCustomEvent(
                                    e.copy(title = eventTitle.trim(), icon = eventIcon, timestamp = eventTimestamp)
                                )
                            } else {
                                viewModel.addCustomEvent(eventTitle.trim(), eventIcon, eventTimestamp)
                            }
                            showEventDialog = false
                            editingEvent = null
                        }
                    },
                    enabled = eventTitle.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEventDialog = false
                    editingEvent = null
                }) { Text("取消") }
            }
        )
    }

    // ── Delete confirmation ─────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确定删除吗？") },
            text = { Text("删除后无法恢复，所有数据（包括日历事件）将被清除。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteExam(examId)
                    showDeleteDialog = false
                    onNavigateBack()
                }) { Text("删除") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    // ── Main UI ─────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考试详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val exam = state.exam ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Header ───────────────────────────────────────────
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(300)),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exam.unitName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { copyToClipboard("招聘单位", exam.unitName) }
                                )
                                if (exam.positionName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = exam.positionName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.clickable { copyToClipboard("岗位名称", exam.positionName) }
                                    )
                                }
                            }
                            StatusChip(status = getExamStatus(exam.regEndTime, exam.examTime))
                        }
                        if (exam.positionCode.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "岗位代码：${exam.positionCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { copyToClipboard("岗位代码", exam.positionCode) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { requestCalendarPermissionAndSync() },
                            enabled = !state.isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            }
                            Text(if (state.isSyncing) "同步中…" else "同步公告时间点到系统日历", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 报名信息 ─────────────────────────────────────────
            SectionTitle("报名信息")
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400, delayMillis = 50)),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("我的报名记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (isEditingAccount) {
                                IconButton(onClick = {
                                    viewModel.saveAccountInfo(examId, editAccount, editPassword, editRegPosName, editRegPosCode)
                                    isEditingAccount = false
                                }) { Icon(Icons.Default.Save, contentDescription = "保存", tint = MaterialTheme.colorScheme.primary) }
                            } else {
                                IconButton(onClick = {
                                    editAccount = exam.account
                                    editPassword = exam.accountPassword
                                    editRegPosName = exam.registeredPositionName
                                    editRegPosCode = exam.registeredPositionCode
                                    isEditingAccount = true
                                }) { Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.height(18.dp)) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isEditingAccount) {
                            OutlinedTextField(editAccount, { editAccount = it }, label = { Text("报名账号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(editPassword, { editPassword = it }, label = { Text("报名密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(editRegPosName, { editRegPosName = it }, label = { Text("报名岗位名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(editRegPosCode, { editRegPosCode = it }, label = { Text("报名岗位代码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        } else {
                            val hasData = exam.account.isNotBlank() || exam.accountPassword.isNotBlank() ||
                                    exam.registeredPositionName.isNotBlank() || exam.registeredPositionCode.isNotBlank()
                            if (!hasData) {
                                Text("点击右侧编辑按钮记录报名信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            } else {
                                if (exam.account.isNotBlank()) CopyRow("报名账号", exam.account, context)
                                if (exam.accountPassword.isNotBlank()) CopyRow("报名密码", exam.accountPassword, context)
                                if (exam.registeredPositionName.isNotBlank()) CopyRow("报名岗位", exam.registeredPositionName, context)
                                if (exam.registeredPositionCode.isNotBlank()) CopyRow("报名代码", exam.registeredPositionCode, context)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 基本信息 ─────────────────────────────────────────
            SectionTitle("基本信息")
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400, delayMillis = 100)),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (exam.orgType.isNotBlank()) CopyRow("单位性质", exam.orgType, context)
                        if (exam.workLocation.isNotBlank()) CopyRow("工作地点", exam.workLocation, context)
                        if (exam.totalRecruitment.isNotBlank()) CopyRow("招聘人数", exam.totalRecruitment, context)
                        if (exam.announcementUrl.isNotBlank()) LinkRow("公告链接", exam.announcementUrl, context)
                        if (exam.registrationUrl.isNotBlank()) LinkRow("报名网址", exam.registrationUrl, context)
                        if (exam.examSubjects.isNotBlank()) CopyRow("笔试科目", exam.examSubjects, context)
                        if (exam.examPassLine.isNotBlank()) CopyRow("合格线", exam.examPassLine, context)
                        if (exam.interviewFormat.isNotBlank()) CopyRow("面试形式", exam.interviewFormat, context)
                        if (exam.scoreFormula.isNotBlank()) CopyRow("成绩计算", exam.scoreFormula, context)
                        if (exam.examFee.isNotBlank()) CopyRow("报名费", exam.examFee, context)
                        if (exam.notes.isNotBlank()) CopyRow("备注", exam.notes, context)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 公告时间线 ───────────────────────────────────────
            SectionTitle("公告时间线")
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400, delayMillis = 200)),
            ) { TimelineView(items = buildTimelineItems(exam)) }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 自定义时间点 ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("自定义时间点")
                IconButton(onClick = {
                    editingEvent = null
                    eventTitle = ""
                    eventIcon = "📌"
                    eventTimestamp = System.currentTimeMillis()
                    showEventDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (state.customEvents.isEmpty()) {
                Text(
                    text = "暂无自定义时间点，点击右侧 + 添加\n如：面试材料审核、体检、政审等",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            } else {
                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessLow)) + fadeIn(tween(400)),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            state.customEvents.forEach { event ->
                                CustomEventRow(
                                    event = event,
                                    isSyncing = state.customEventSyncingId == event.id,
                                    onEdit = {
                                        editingEvent = event
                                        eventTitle = event.title
                                        eventIcon = event.icon
                                        eventTimestamp = event.timestamp
                                        showEventDialog = true
                                    },
                                    onDelete = { viewModel.deleteCustomEvent(event) },
                                    onSync = { viewModel.syncCustomEvent(event) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Custom event row ──────────────────────────────────────────────────

@Composable
private fun CustomEventRow(
    event: CustomTimelineEvent,
    isSyncing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = event.icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = formatDateFull(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onSync, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Sync, contentDescription = "同步到日历", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CopyRow(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.width(80.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.padding(start = 4.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (url.isNotBlank()) {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (_: Exception) { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.width(80.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, modifier = Modifier.weight(1f))
            Icon(Icons.Default.OpenInBrowser, contentDescription = "打开", modifier = Modifier.padding(start = 4.dp).height(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
    }
}

private fun buildTimelineItems(exam: ExamEntity): List<TimelineItem> {
    val now = System.currentTimeMillis()
    val items = mutableListOf<TimelineItem>()
    exam.regStartTime?.let { items.add(TimelineItem("报名开始", formatDateFull(it), it, it < now, "📝")) }
    exam.regEndTime?.let { items.add(TimelineItem("报名截止", formatDateFull(it), it, it < now, "📝")) }
    exam.reviewEndTime?.let { items.add(TimelineItem("资格初审截止", formatDateFull(it), it, it < now, "🔍")) }
    exam.paymentEndTime?.let { items.add(TimelineItem("缴费截止", formatDateFull(it), it, it < now, "💰")) }
    exam.admitCardStart?.let { items.add(TimelineItem("准考证打印", formatDateFull(it), it, it < now, "🎫")) }
    exam.examTime?.let { items.add(TimelineItem("笔试", formatDateFull(it), it, it < now, "✏️")) }
    exam.scorePublishTime?.let { items.add(TimelineItem("成绩公布", formatDateFull(it), it, it < now, "📊")) }
    exam.qualificationReviewTime?.let { items.add(TimelineItem("资格复审", formatDateFull(it), it, it < now, "📋")) }
    exam.interviewTime?.let { items.add(TimelineItem("面试", formatDateFull(it), it, it < now, "🎤")) }
    return items.sortedBy { it.timestamp ?: Long.MAX_VALUE }
}
