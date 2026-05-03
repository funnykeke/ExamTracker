package com.examtracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

object WebFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    )

    data class FetchResult(
        val success: Boolean,
        val title: String = "",
        val textContent: String = "",
        val htmlContent: String = "",
        val error: String = ""
    )

    suspend fun fetchPage(url: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgents.random())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Charset", "utf-8, gbk, gb2312;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            val bodyBytes = response.body?.bytes() ?: return@withContext FetchResult(
                success = false,
                error = "页面内容为空"
            )

            val contentType = response.header("Content-Type") ?: ""
            val charset = when {
                contentType.contains("gbk", ignoreCase = true) ||
                        contentType.contains("gb2312", ignoreCase = true) -> "GBK"
                contentType.contains("utf-8", ignoreCase = true) -> "UTF-8"
                else -> detectCharset(bodyBytes)
            }

            val html = String(bodyBytes, charset(charset))
            val doc: Document = Jsoup.parse(html)

            doc.select("script, style, nav, footer, header, .header, .footer, .nav, .sidebar, .comment").remove()

            val textContent = doc.body().text().replace(Regex("\\s+"), " ").trim() ?: ""
            val title = doc.title()

            FetchResult(
                success = true,
                title = title,
                textContent = textContent.take(15000),
                htmlContent = html.take(50000)
            )
        } catch (e: Exception) {
            FetchResult(
                success = false,
                error = e.message ?: "未知错误"
            )
        }
    }

    private fun detectCharset(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(1024, bytes.size), Charsets.UTF_8)
        val patterns = listOf(
            Regex("""charset=["']?gbk""", RegexOption.IGNORE_CASE),
            Regex("""charset=["']?gb2312""", RegexOption.IGNORE_CASE),
            Regex("""charset=["']?gb18030""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            if (pattern.containsMatchIn(head)) return "GBK"
        }
        return "UTF-8"
    }
}
