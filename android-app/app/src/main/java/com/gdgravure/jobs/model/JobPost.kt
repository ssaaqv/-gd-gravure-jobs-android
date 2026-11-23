package com.gdgravure.jobs.model

enum class FollowStatus(val label: String) {
    UNREAD("未联系"),
    CONTACTED("已打电话"),
    INTERVIEW("已约试机"),
    BLOCKED("避坑屏蔽")
}

data class JobPost(
    val id: String,
    val title: String,
    val company: String,
    val city: String,
    val role: String,
    val salaryText: String,
    val salaryMin: Int,
    val salaryMax: Int,
    val salaryType: String,
    val shift: String,
    val workDays: String,
    val machineTags: List<String>,
    val materialTags: List<String>,
    val benefitTags: List<String>,
    val phone: String,
    val wechat: String,
    val contactName: String,
    val rawText: String,
    val sourceName: String,
    val sourceUrl: String,
    val publishedAt: String,
    val repeatCount: Int = 1,
    val isManualImport: Boolean = false
)
