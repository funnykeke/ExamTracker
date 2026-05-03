package com.examtracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object SiliconFlowApi {

    private const val BASE_URL = "https://api.siliconflow.cn/v1/chat/completions"
    private const val MODEL = "deepseek-ai/DeepSeek-V3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    // --- Request models ---
    @Serializable
    data class ChatMessage(val role: String, val content: String)

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.0,
        @SerialName("max_tokens") val maxTokens: Int = 4096,
        val stream: Boolean = false
    )

    // --- Response models ---
    @Serializable
    data class ChatChoice(val message: ChatMessage)

    @Serializable
    data class ChatResponse(val choices: List<ChatChoice>)

    // --- Extracted exam data ---
    @Serializable
    data class ExtractedExam(
        val unitName: String = "",
        val positionName: String = "",
        val positionCode: String = "",
        val totalRecruitment: String = "",
        val orgType: String = "",
        val workLocation: String = "",
        val account: String = "",
        val registrationUrl: String = "",
        val regStartTime: String = "",
        val regEndTime: String = "",
        val reviewEndTime: String = "",
        val paymentEndTime: String = "",
        val admitCardStart: String = "",
        val admitCardEnd: String = "",
        val examTime: String = "",
        val examSubjects: String = "",
        val examPassLine: String = "",
        val scorePublishTime: String = "",
        val qualificationReviewTime: String = "",
        val interviewTime: String = "",
        val interviewFormat: String = "",
        val scoreFormula: String = "",
        val examFee: String = "",
        val notes: String = ""
    )

    data class ParsedResult(
        val success: Boolean,
        val exam: ExtractedExam? = null,
        val error: String = ""
    )

    suspend fun extractExamInfo(
        apiKey: String,
        pageContent: String,
        pageTitle: String,
        url: String
    ): ParsedResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ParsedResult(false, error = "请先设置硅基流动 API Key")
        }

        val systemPrompt = buildString {
            append("你是一个专业的招聘信息提取助手。用户会提供一份事业单位/教师招聘公告内容。")
            append("请仔细阅读公告，从中提取以下信息，并以 JSON 格式返回。")
            append("所有日期时间字段请转换为 \"yyyy-MM-dd HH:mm\" 格式（如无法确定具体时间，只保留日期部分如\"2026-05-10\"）；")
            append("无法从公告中确定的字段置为空字符串。")
            append("\n\n返回字段：unitName(招聘单位), positionName(岗位名称), positionCode(岗位代码), ")
            append("totalRecruitment(招聘总人数), orgType(单位性质), workLocation(工作地点), ")
            append("account(报名账号/身份证号，公告中有提及则填写), registrationUrl(报名网址), ")
            append("regStartTime(报名开始时间), regEndTime(报名截止时间), ")
            append("reviewEndTime(资格初审截止), paymentEndTime(缴费截止), admitCardStart(准考证打印开始), ")
            append("admitCardEnd(准考证打印截止), examTime(笔试时间), examSubjects(笔试科目), ")
            append("examPassLine(笔试合格线), scorePublishTime(成绩公布时间), qualificationReviewTime(资格复审时间), ")
            append("interviewTime(面试时间), interviewFormat(面试形式), scoreFormula(总成绩计算), examFee(报名费), notes(备注)")
        }

        val userMessage = "公告来源：$url\n公告标题：$pageTitle\n\n公告内容：\n$pageContent"

        val requestBody = ChatRequest(
            model = MODEL,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userMessage)
            ),
            temperature = 0.0,
            maxTokens = 4096
        )

        try {
            val bodyStr = json.encodeToString(requestBody)

            val request = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(bodyStr.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ParsedResult(false, error = "API 返回错误(${response.code}): ${responseBody.take(200)}")
            }

            val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
            val contentText = chatResponse.choices.firstOrNull()?.message?.content ?: ""
            if (contentText.isBlank()) {
                return@withContext ParsedResult(false, error = "AI 返回内容为空，请重试")
            }

            val jsonText = extractJsonFromText(contentText)
            val extracted = json.decodeFromString<ExtractedExam>(jsonText)
            ParsedResult(true, exam = extracted)

        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            ParsedResult(false, error = "提取失败: $msg")
        }
    }

    suspend fun extractFromPastedText(apiKey: String, text: String): ParsedResult {
        return extractExamInfo(apiKey, text, "", "")
    }

    private fun extractJsonFromText(text: String): String {
        val jsonBlockRegex = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""")
        val match = jsonBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1)
        }

        return text
    }
}
