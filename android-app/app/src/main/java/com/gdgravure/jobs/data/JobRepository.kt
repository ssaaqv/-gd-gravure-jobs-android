package com.gdgravure.jobs.data

import android.content.Context
import com.gdgravure.jobs.model.AiDigest
import com.gdgravure.jobs.model.FeedPayload
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class JobRepository(context: Context) {

    companion object {
        // 替换为你自己 GitHub 仓库的 jobs.json Raw 链接（也可在 App 设置里随时修改）
        const val DEFAULT_FEED_URL =
            "https://raw.githubusercontent.com/ssaaqv/-gd-gravure-jobs-android/main/data/jobs.json"
        private const val PREF_NAME = "gd_gravure_prefs"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_CACHED_REMOTE = "cached_remote_json"
        private const val KEY_MANUAL_JOBS = "manual_jobs_json"
        private const val KEY_STATUS_MAP = "status_map_json"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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

    fun loadCachedRemoteJobs(): FeedPayload {
        val cached = prefs.getString(KEY_CACHED_REMOTE, null)
        if (cached.isNullOrBlank()) {
            return FeedPayload("未同步（点击右上角刷新）", AiDigest(), emptyList())
        }
        return parseFeedJson(cached)
    }

    suspend fun fetchRemoteJobs(): Result<FeedPayload> = withContext(Dispatchers.IO) {
        val urlStr = getFeedUrl()
        if (urlStr.contains("YOUR_USERNAME")) {
            return@withContext Result.failure(
                IllegalStateException("请先点击右上角设置图标，填入你自己的 jobs.json 链接")
            )
        }
        runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 12000
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            prefs.edit().putString(KEY_CACHED_REMOTE, body).apply()
            parseFeedJson(body)
        }
    }

    private fun parseFeedJson(raw: String): FeedPayload {
        val root = JSONObject(raw)
        val updatedAt = root.optString("updated_at", "未知时间")
        val digestObj = root.optJSONObject("ai_digest")
        val digest = if (digestObj != null) {
            AiDigest(
                generatedAt = digestObj.optString("generated_at", ""),
                mode = digestObj.optString("mode", "AI精选"),
                summary = digestObj.optString("summary", "")
            )
        } else {
            AiDigest()
        }
        val arr = root.optJSONArray("jobs") ?: JSONArray()
        return FeedPayload(updatedAt, digest, parseJobsArray(arr, isManual = false))
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
                    sourceName = o.optString("source_name", if (isManual) "手动导入" else "垂直源"),
                    sourceUrl = o.optString("source_url", ""),
                    publishedAt = o.optString("published_at", ""),
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
