package com.examtracker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebSearcher {

    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String,
        val source: String = ""
    )

    data class SearchResponse(
        val success: Boolean,
        val results: List<SearchResult> = emptyList(),
        val error: String = ""
    )

    // CookieJar to maintain session cookies across requests — helps bypass basic anti-scraping
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies.toMutableList()
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private val desktopUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val mobileUA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    suspend fun search(keyword: String, maxResults: Int = 15): SearchResponse =
        withContext(Dispatchers.IO) {
            val query = "$keyword 招聘公告"

            // 1) 搜狗 — 国内可用，HTML 较简单
            val sogouResult = searchSogou(query, maxResults)
            if (sogouResult.success && sogouResult.results.isNotEmpty()) return@withContext sogouResult

            // 2) Bing
            val bingResult = searchBing(query, maxResults)
            if (bingResult.success && bingResult.results.isNotEmpty()) return@withContext bingResult

            // 3) 百度移动版
            val baiduResult = searchBaidu(query, maxResults)
            if (baiduResult.success && baiduResult.results.isNotEmpty()) return@withContext baiduResult

            SearchResponse(
                success = false,
                error = buildString {
                    append("搜索失败")
                    if (sogouResult.error.isNotBlank()) append("\n搜狗: ${sogouResult.error}")
                    if (bingResult.error.isNotBlank()) append("\nBing: ${bingResult.error}")
                    if (baiduResult.error.isNotBlank()) append("\n百度: ${baiduResult.error}")
                }
            )
        }

    // ── 搜狗搜索 ────────────────────────────────────────────────────

    private fun searchSogou(query: String, max: Int): SearchResponse {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.sogou.com/web?query=$encoded&num=$max"
            val body = fetchWithUA(url, desktopUA)
            if (body.isBlank()) return SearchResponse(false, error = "响应为空")

            val doc = Jsoup.parse(body)
            val results = mutableListOf<SearchResult>()

            // 搜狗多种结果容器
            val containers = doc.select("div.results > div.rb, div.vrwrap, div.vrwrap-new")

            if (containers.isEmpty()) {
                return SearchResponse(false, error = "未匹配到搜索结果容器 (results=${doc.text().take(100)})")
            }

            for (el in containers) {
                if (results.size >= max) break

                // 多种标题选择器
                val titleEl = el.selectFirst("h3.vrTitle a")
                    ?: el.selectFirst("h3 a")
                    ?: el.selectFirst("a[href]")
                    ?: continue

                val title = titleEl.text().trim()
                val href = titleEl.attr("href")

                if (title.isBlank() || href.isBlank() || !href.startsWith("http")) continue
                // 跳过搜狗内部链接
                if (href.contains("sogou.com") && !href.contains("/link?")) continue

                val snippet = el.selectFirst("div.star-wiki, div.str-text, div.space-txt, p.str-text, div.str_info_div, div.str-text, div.fb-hint, p")?.text()?.trim() ?: ""
                val cite = el.selectFirst("cite, .cite, .source, p.src-site")?.text()?.trim() ?: ""

                results.add(SearchResult(title, href, snippet, cite))
            }

            if (results.isEmpty()) SearchResponse(false, error = "解析结果为空 (容器=${containers.size})")
            else SearchResponse(true, results)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SearchResponse(false, error = msg.take(100))
        }
    }

    // ── Bing ────────────────────────────────────────────────────────

    private fun searchBing(query: String, max: Int): SearchResponse {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            // try www.bing.com with Chinese locale
            val url = "https://www.bing.com/search?q=$encoded&count=$max&setlang=zh-Hans&cc=CN&mkt=zh-CN"
            val body = fetchWithUA(url, desktopUA)
            if (body.isBlank()) return SearchResponse(false, error = "响应为空")

            val doc = Jsoup.parse(body)

            // Bing result items — try multiple selectors
            var items = doc.select("li.b_algo")
            if (items.isEmpty()) {
                items = doc.select("ol#b_results > li.b_algo")
            }
            if (items.isEmpty()) {
                items = doc.select("#b_results > li.b_algo")
            }
            if (items.isEmpty()) {
                items = doc.select("li.b_ans, li.b_algo")
            }
            if (items.isEmpty()) {
                // Bing might return a different page
                val bodyText = doc.body().text().take(200)
                return SearchResponse(false, error = "未找到结果项 (body=${bodyText})")
            }

            val results = items.take(max).mapNotNull { el ->
                val linkEl = el.selectFirst("h2 a") ?: el.selectFirst("a[href]") ?: return@mapNotNull null
                val title = linkEl.text().trim()
                val href = linkEl.attr("href")
                val snippet = el.selectFirst(".b_caption p")?.text()
                    ?: el.selectFirst(".b_lineclamp2")?.text()
                    ?: el.selectFirst(".b_lineclamp3")?.text()
                    ?: el.selectFirst("p")?.text()
                    ?: ""
                val cite = el.selectFirst("cite")?.text()
                    ?: el.selectFirst(".b_attribution")?.text()
                    ?: ""
                if (title.isBlank() || href.isBlank()) return@mapNotNull null
                SearchResult(title, href, snippet, cite)
            }

            if (results.isEmpty()) SearchResponse(false, error = "解析结果为空 (items=${items.size})")
            else SearchResponse(true, results)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SearchResponse(false, error = msg.take(100))
        }
    }

    // ── 百度 ────────────────────────────────────────────────────────

    private fun searchBaidu(query: String, max: Int): SearchResponse {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://m.baidu.com/s?word=$encoded&rn=$max"
            val body = fetchWithUA(url, mobileUA)
            if (body.isBlank()) return SearchResponse(false, error = "响应为空")

            val doc = Jsoup.parse(body)

            // 百度移动版结果
            val items = doc.select("div.result, div.c-result, div.c-container, section.result")

            if (items.isEmpty()) {
                return SearchResponse(false, error = "未匹配到结果项 (body=${doc.body().text().take(100)})")
            }

            val results = items.take(max).mapNotNull { el ->
                // skip Baidu's own cards/widgets
                val linkEl = el.selectFirst("a[href]") ?: return@mapNotNull null
                val title = linkEl.text().trim()
                val href = linkEl.attr("href")
                if (title.isBlank()) return@mapNotNull null

                val snippet = el.selectFirst("div.c-abstract")?.text()
                    ?: el.selectFirst("div.c-summary")?.text()
                    ?: el.selectFirst("p")?.text()
                    ?: ""
                SearchResult(title, href, snippet, "百度")
            }

            if (results.isEmpty()) SearchResponse(false, error = "解析结果为空 (items=${items.size})")
            else SearchResponse(true, results)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SearchResponse(false, error = msg.take(100))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun fetchWithUA(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Cache-Control", "no-cache")
            .header("DNT", "1")
            .build()

        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }
}
