package com.gdgravure.jobs.data

import android.content.Context
import com.gdgravure.jobs.model.FeedPayload
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost
import com.gdgravure.jobs.parser.LocalTextParser
import com.gdgravure.jobs.scraper.DirectScraper
import com.gdgravure.jobs.scraper.WebViewScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JobRepository(private val appContext: Context) {

    companion object {
        const val DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/data/jobs.json"
        private const val PREF_NAME = "gd_gravure_prefs"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_CACHED_JOBS = "cached_jobs_json_v11"
        private const val KEY_UPDATED_AT = "cached_updated_at_v11"
        private const val KEY_MANUAL_JOBS = "manual_jobs_json"
        private const val KEY_STATUS_MAP = "status_map_json"
        private const val KEY_BLACKLIST_PHONES = "blacklist_phones_set"
    }

    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getFeedUrl(): String = prefs.getString(KEY_FEED_URL, DEFAULT_FEED_URL) ?: DEFAULT_FEED_URL

    fun saveFeedUrl(url: String) {
        prefs.edit().putString(KEY_FEED_URL, url.trim()).apply()
    }

    fun getStatusMap(): Map<String, FollowStatus> {
        val raw = prefs.getString(KEY_STATUS_MAP, "{}") ?: "{}"
        val obj = JSONObject(raw)
        val result = mutableMapOf<String, FollowStatus>()
        obj.keys().forEach { key ->
            val value = obj.optString(key)
            result[key] = FollowStatus.entries.firstOrNull { it.name == value } ?: FollowStatus.UNREAD
        }
        return result
    }

    fun getBlacklistedPhones(): Set<String> = prefs.getStringSet(KEY_BLACKLIST_PHONES, emptySet()) ?: emptySet()

    fun blacklistPhone(phone: String) {
        if (phone.isBlank()) return
        val next = getBlacklistedPhones().toMutableSet().apply { add(phone) }
        prefs.edit().putStringSet(KEY_BLACKLIST_PHONES, next).apply()
    }

    fun setStatus(jobId: String, status: FollowStatus) {
        val raw = prefs.getString(KEY_STATUS_MAP, "{}") ?: "{}"
        val obj = JSONObject(raw)
        obj.put(jobId, status.name)
        prefs.edit().putString(KEY_STATUS_MAP, obj.toString()).apply()
    }

    fun getManualJobs(): List<JobPost> {
        val raw = prefs.getString(KEY_MANUAL_JOBS, "[]") ?: "[]"
        return parseJobsArray(JSONArray(raw), isManual = true)
    }

    fun addManualJob(job: JobPost) {
        val list = getManualJobs().toMutableList()
        list.add(0, job)
        val arr = JSONArray()
        list.forEach { arr.put(jobToJson(it)) }
        prefs.edit().putString(KEY_MANUAL_JOBS, arr.toString()).apply()
    }

    fun loadCachedFeed(): FeedPayload {
        val raw = prefs.getString(KEY_CACHED_JOBS, "[]") ?: "[]"
        val updated = prefs.getString(KEY_UPDATED_AT, "首次启动（点击刷新直抓国内站）") ?: ""
        val jobs = parseJobsArray(JSONArray(raw), isManual = false)
        val digest = LocalTextParser.buildLocalDigest(jobs)
        return FeedPayload(updated, digest, jobs)
    }

    /**
     * 中国本地直抓（主力）+ 可选远程 JSON 补充，返回“本次新增的岗位列表”。
     */
    suspend fun refreshAllLocalAndRemote(): List<JobPost> = withContext(Dispatchers.IO) {
        val oldList = loadCachedFeed().jobs
        val oldIds = oldList.map { it.id }.toSet()

        val merged = linkedMapOf<String, JobPost>()
        oldList.forEach { merged[it.id] = it }

        // 1. 手机国内网络 8 路并发直连抓取 + WebView 动态站渲染抓取
        val directRes = runCatching { DirectScraper.scrapeAllDomestic() }.getOrNull()
        directRes?.posts?.forEach { merged[it.id] = it }

        val webRes = runCatching { WebViewScraper.scrapeDynamicSites(appContext) }.getOrNull()
        webRes?.first?.forEach { merged[it.id] = it }

        val combinedStats = linkedMapOf<String, Int>()
        directRes?.sourceStats?.let { combinedStats.putAll(it) }
        webRes?.second?.let { combinedStats.putAll(it) }
        val statSummary = combinedStats.entries.filter { it.value > 0 }
            .joinToString("/") { "${it.key}${it.value}" }

        // 2. 如果配置了云端 jobs.json 也顺便拉取合并
        val urlStr = getFeedUrl()
        if (!urlStr.contains("YOUR_USERNAME") && urlStr.startsWith("http")) {
            runCatching {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val arr = JSONObject(body).optJSONArray("jobs") ?: JSONArray()
                    parseJobsArray(arr, isManual = false).forEach { merged[it.id] = it }
                }
            }
        }

        val cutoff45 = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(System.currentTimeMillis() - 45L * 86400_000L))
        val allList = merged.values.filter { it.publishedAt.length < 10 || it.publishedAt >= cutoff45 }.toMutableList()
        val phoneCounts = allList.filter { it.phone.isNotBlank() }.groupingBy { it.phone }.eachCount()
        val finalList = allList.map { j ->
            val cnt = if (j.phone.isNotBlank()) (phoneCounts[j.phone] ?: 1) else 1
            j.copy(repeatCount = cnt)
        }.sortedByDescending { it.publishedAt }

        val arr = JSONArray()
        finalList.forEach { arr.put(jobToJson(it)) }
        val nowStr = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date())

        prefs.edit()
            .putString(KEY_CACHED_JOBS, arr.toString())
            .putString(KEY_UPDATED_AT, if (statSummary.isNotBlank()) "$nowStr（$statSummary）" else "$nowStr（本地直抓）")
            .apply()

        finalList.filter { it.id !in oldIds }
    }

    private fun parseJobsArray(arr: JSONArray, isManual: Boolean): List<JobPost> {
        val list = mutableListOf<JobPost>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                JobPost(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    company = o.optString("company", "广东软包装企业"),
                    city = o.optString("city", "广东"),
                    role = o.optString("role", "凹印相关"),
                    salaryText = o.optString("salary_text", "面议"),
                    salaryMin = o.optInt("salary_min", 0),
                    salaryMax = o.optInt("salary_max", 0),
                    salaryType = o.optString("salary_type", "面议"),
                    shift = o.optString("shift", "未知"),
                    workDays = o.optString("work_days", "未知"),
                    machineTags = jsonArrayToList(o.optJSONArray("machine_tags")),
                    materialTags = jsonArrayToList(o.optJSONArray("material_tags")),
                    benefitTags = jsonArrayToList(o.optJSONArray("benefit_tags")),
                    phone = o.optString("phone", ""),
                    wechat = o.optString("wechat", ""),
                    contactName = o.optString("contact_name", ""),
                    rawText = o.optString("raw_text", ""),
                    sourceName = o.optString("source_name", if (isManual) "手动导入" else "国内直抓"),
                    sourceUrl = o.optString("source_url", ""),
                    publishedAt = LocalTextParser.normalizeDate(o.optString("published_at", "")),
                    repeatCount = o.optInt("repeat_count", 1),
                    isManualImport = isManual
                )
            )
        }
        return list
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun jobToJson(j: JobPost): JSONObject = JSONObject().apply {
        put("id", j.id)
        put("title", j.title)
        put("company", j.company)
        put("city", j.city)
        put("role", j.role)
        put("salary_text", j.salaryText)
        put("salary_min", j.salaryMin)
        put("salary_max", j.salaryMax)
        put("salary_type", j.salaryType)
        put("shift", j.shift)
        put("work_days", j.workDays)
        put("machine_tags", JSONArray(j.machineTags))
        put("material_tags", JSONArray(j.materialTags))
        put("benefit_tags", JSONArray(j.benefitTags))
        put("phone", j.phone)
        put("wechat", j.wechat)
        put("contact_name", j.contactName)
        put("raw_text", j.rawText)
        put("source_name", j.sourceName)
        put("source_url", j.sourceUrl)
        put("published_at", j.publishedAt)
        put("repeat_count", j.repeatCount)
    }
}
