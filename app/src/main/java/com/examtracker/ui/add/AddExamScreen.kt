package com.examtracker.ui.add

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.examtracker.data.db.ExamEntity
import com.examtracker.ui.components.formatDateFull

private val animFast = tween<Float>(200)
private val animMed = tween<Float>(300)
private val animSlide = tween<IntOffset>(300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExamScreen(
    onNavigateBack: () -> Unit,
    onExamSaved: (Long) -> Unit,
    viewModel: AddExamViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var tabIndex by remember { mutableIntStateOf(2) }

    // 从 WebView 返回后自动切到公告链接 tab
    LaunchedEffect(state.activeTab) {
        tabIndex = state.activeTab
    }

    // WebView 搜索弹窗
    if (state.showWebSearch) {
        WebSearchDialog(
            url = state.webSearchUrl,
            onPageConfirmed = { pageUrl ->
                viewModel.onWebSearchResult(pageUrl)
            },
            onDismiss = { viewModel.dismissWebSearch() }
        )
        // 弹窗打开时不渲染下层 UI，减少掉帧
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加考试") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ── API Key ─────────────────────────────────────────────
            ApiKeySection(
                apiKey = state.apiKey,
                hasSavedKey = state.hasSavedKey,
                onUpdateKey = viewModel::updateApiKey,
                onSaveKey = viewModel::saveApiKeyAndContinue,
                onEditKey = viewModel::editSavedKey,
                onClearKey = viewModel::clearSavedKey
            )

            // ── Tabs ─────────────────────────────────────────────────
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("公告链接") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("粘贴内容") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("搜索公告") })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Tab content ──────────────────────────────────────────
            val contentVisible = remember(tabIndex) { mutableStateOf(false) }
            LaunchedEffect(tabIndex) { contentVisible.value = true }

            AnimatedVisibility(
                visible = contentVisible.value,
                enter = fadeIn(animFast) + slideInVertically(animSlide) { it / 6 },
                exit = fadeOut(animFast)
            ) {
                Column {
                    when (tabIndex) {
                        0 -> UrlTabContent(state, viewModel)
                        1 -> PasteTabContent(state, viewModel)
                        2 -> SearchTabContent(state, viewModel)
                    }
                }
            }

            // ── 提取进度（全局统一反馈） ──────────────────────────────
            AnimatedVisibility(
                visible = state.isExtracting,
                enter = fadeIn(animFast),
                exit = fadeOut(animFast)
            ) {
                ExtractionProgressCard(
                    source = if (state.url.isNotBlank()) state.url
                        .replace(Regex("^https?://"), "")
                        .take(40) + "…"
                    else "粘贴文本"
                )
            }

            // ── 提取结果 ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.showPreview && state.extractedExam != null,
                enter = fadeIn(animMed),
                exit = fadeOut(animFast)
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "确认提取结果",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    state.extractedExam?.let { ExamPreviewCard(it) }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.confirmSave() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("确认保存") }
                }
            }

            // ── 保存成功 ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.savedSuccessfully,
                enter = fadeIn(animMed),
                exit = fadeOut(animFast)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("保存成功！", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val id = state.extractedExam?.id ?: return@Button
                                onExamSaved(id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("查看详情") }
                    }
                }
            }

            // ── 错误提示 ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = state.error.isNotBlank(),
                enter = fadeIn(animFast),
                exit = fadeOut(animFast)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = state.error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── 提取进度卡片 ──────────────────────────────────────────────────────

@Composable
private fun ExtractionProgressCard(source: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp).width(20.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "正在智能提取…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "来源：$source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

// ── Tab 内容 ──────────────────────────────────────────────────────────

@Composable
private fun UrlTabContent(state: AddExamState, viewModel: AddExamViewModel) {
    OutlinedTextField(
        value = state.url,
        onValueChange = viewModel::updateUrl,
        label = { Text("公告链接") },
        placeholder = { Text("https://...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.positionName,
            onValueChange = viewModel::updatePositionName,
            label = { Text("岗位名称（选填）") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = state.positionCode,
            onValueChange = viewModel::updatePositionCode,
            label = { Text("岗位代码（选填）") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    ExtractButton(state.isExtracting) { viewModel.startExtraction() }
}

@Composable
private fun PasteTabContent(state: AddExamState, viewModel: AddExamViewModel) {
    OutlinedTextField(
        value = state.pastedText,
        onValueChange = viewModel::updatePastedText,
        label = { Text("粘贴公告全文") },
        placeholder = { Text("将公告内容粘贴到此处…") },
        modifier = Modifier.fillMaxWidth().height(200.dp),
        maxLines = 20
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.positionName,
            onValueChange = viewModel::updatePositionName,
            label = { Text("岗位名称（选填）") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = state.positionCode,
            onValueChange = viewModel::updatePositionCode,
            label = { Text("岗位代码（选填）") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    ExtractButton(state.isExtracting) { viewModel.startExtraction() }
}

@Composable
private fun SearchTabContent(state: AddExamState, viewModel: AddExamViewModel) {
    OutlinedTextField(
        value = state.searchKeyword,
        onValueChange = viewModel::updateSearchKeyword,
        label = { Text("搜索关键字") },
        placeholder = { Text("例如：苏州市教育局 招聘") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { viewModel.openWebSearch() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { viewModel.openWebSearch() })
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "点击搜索后将打开内置浏览器，找到公告页面后\n点击底部「使用此页面」即可自动提取。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    )
}

@Composable
private fun ExtractButton(isExtracting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isExtracting
    ) {
        if (isExtracting) {
            CircularProgressIndicator(
                modifier = Modifier.height(18.dp).width(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("正在智能提取…")
        } else {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("智能提取")
        }
    }
}

// ── WebView 全屏搜索 ──────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebSearchDialog(
    url: String,
    onPageConfirmed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(url) }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("搜索公告", style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.mixedContentMode =
                                android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    pageUrl: String?,
                                    favicon: Bitmap?
                                ) {
                                    if (pageUrl != null) currentUrl = pageUrl
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    isLoading = false
                                }
                            }

                            webChromeClient = WebChromeClient()
                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 顶部加载条
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }

                // 底部操作栏
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement
                            .spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) { Text("取消") }
                        Button(
                            onClick = { onPageConfirmed(currentUrl) },
                            modifier = Modifier.weight(1.5f),
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.height(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("使用此页面")
                        }
                    }
                }
            }
        }
    }
}

// ── API Key ───────────────────────────────────────────────────────────

@Composable
private fun ApiKeySection(
    apiKey: String,
    hasSavedKey: Boolean,
    onUpdateKey: (String) -> Unit,
    onSaveKey: () -> Unit,
    onEditKey: () -> Unit,
    onClearKey: () -> Unit
) {
    if (hasSavedKey) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Key: ${apiKey.take(8)}...${apiKey.takeLast(4)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onEditKey, modifier = Modifier.height(32.dp)) {
                    Text("修改", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onClearKey,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("删除", style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        return
    }

    OutlinedTextField(
        value = apiKey,
        onValueChange = onUpdateKey,
        label = { Text("硅基流动 API Key") },
        placeholder = { Text("sk-...") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(modifier = Modifier.height(6.dp))
    Button(
        onClick = onSaveKey,
        modifier = Modifier.fillMaxWidth(),
        enabled = apiKey.isNotBlank()
    ) { Text(if (apiKey.isNotBlank()) "保存 Key 并继续" else "请先设置 API Key") }
    Spacer(modifier = Modifier.height(12.dp))
}

// ── 预览卡片 ──────────────────────────────────────────────────────────

@Composable
private fun ExamPreviewCard(exam: ExamEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PreviewField("招聘单位", exam.unitName)
            PreviewField("岗位名称", exam.positionName)
            PreviewField("岗位代码", exam.positionCode)
            PreviewField("招聘人数", exam.totalRecruitment)
            PreviewField("单位性质", exam.orgType)
            PreviewField("工作地点", exam.workLocation)
            PreviewField("报名网址", exam.registrationUrl)
            PreviewField("报名时间", buildTimeRange(exam.regStartTime, exam.regEndTime))
            PreviewField("资格初审截止", formatDateFull(exam.reviewEndTime))
            PreviewField("缴费截止", formatDateFull(exam.paymentEndTime))
            PreviewField("准考证打印", buildTimeRange(exam.admitCardStart, exam.admitCardEnd))
            PreviewField("笔试时间", formatDateFull(exam.examTime))
            PreviewField("笔试科目", exam.examSubjects)
            PreviewField("笔试合格线", exam.examPassLine)
            PreviewField("成绩公布", formatDateFull(exam.scorePublishTime))
            PreviewField("资格复审", formatDateFull(exam.qualificationReviewTime))
            PreviewField("面试时间", formatDateFull(exam.interviewTime))
            PreviewField("面试形式", exam.interviewFormat)
            PreviewField("总成绩计算", exam.scoreFormula)
            PreviewField("报名费", exam.examFee)
            PreviewField("备注", exam.notes)
        }
    }
}

@Composable
private fun PreviewField(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(90.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun buildTimeRange(start: Long?, end: Long?): String {
    if (start == null && end == null) return ""
    val startStr = if (start != null) formatDateFull(start) else "?"
    val endStr = if (end != null) formatDateFull(end) else "?"
    return "$startStr — $endStr"
}
