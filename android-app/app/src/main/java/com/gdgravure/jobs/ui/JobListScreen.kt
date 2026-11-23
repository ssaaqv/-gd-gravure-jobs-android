package com.gdgravure.jobs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost

private val CITY_FILTERS = listOf("全部广东", "潮州(庵埠)", "汕头", "东莞", "佛山", "广州", "深圳", "中山", "惠州", "江门")
private val ROLE_FILTERS = listOf("全部岗位", "机长/师傅", "副手/助手", "学徒", "调墨/管理")
private val SHIFT_FILTERS = listOf("不限班次", "长白班", "两班倒")
private val MIN_SALARY_FILTERS = listOf(0 to "不限薪资", 8000 to "≥8K", 10000 to "≥10K", 12000 to "≥12K", 15000 to "≥15K")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    jobs: List<JobPost>,
    updatedAt: String,
    isLoading: Boolean,
    errorMsg: String?,
    statusMap: Map<String, FollowStatus>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onSelectJob: (JobPost) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("全部广东") }
    var selectedRole by remember { mutableStateOf("全部岗位") }
    var selectedShift by remember { mutableStateOf("不限班次") }
    var minSalary by remember { mutableIntStateOf(0) }
    var hideBlocked by remember { mutableStateOf(true) }

    val filteredJobs = remember(jobs, keyword, selectedCity, selectedRole, selectedShift, minSalary, hideBlocked, statusMap) {
        jobs.filter { job ->
            val st = statusMap[job.id] ?: FollowStatus.UNREAD
            if (hideBlocked && st == FollowStatus.BLOCKED) return@filter false
            if (selectedCity != "全部广东" && job.city != selectedCity) return@filter false
            if (selectedRole != "全部岗位" && job.role != selectedRole) return@filter false
            if (selectedShift != "不限班次" && job.shift != selectedShift) return@filter false
            if (minSalary > 0 && job.salaryMax in 1 until minSalary) return@filter false
            if (minSalary > 0 && job.salaryMax == 0) return@filter false
            if (keyword.isNotBlank()) {
                val kw = keyword.trim()
                val hay = "${job.title} ${job.company} ${job.rawText} ${job.machineTags.joinToString()} ${job.materialTags.joinToString()}"
                if (!hay.contains(kw, ignoreCase = true)) return@filter false
            }
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("粤印求职 · 广东塑料袋凹印", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("更新：$updatedAt · 共 ${filteredJobs.size} 条", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "数据源设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenImport,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("粘贴导入") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(SurfaceGray)
        ) {
            // 搜索框（搜机型、膜材、厂名）
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine = true,
                placeholder = { Text("搜机型(电子轴/北人)、膜材(PE/OPP)、镇区...") }
            )

            // 城市快捷筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CITY_FILTERS.forEach { city ->
                    FilterChip(
                        selected = selectedCity == city,
                        onClick = { selectedCity = city },
                        label = { Text(city) }
                    )
                }
            }

            // 岗位 + 班次 + 薪资筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ROLE_FILTERS.forEach { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRole = role },
                        label = { Text(role) }
                    )
                }
                SHIFT_FILTERS.forEach { sh ->
                    FilterChip(
                        selected = selectedShift == sh,
                        onClick = { selectedShift = sh },
                        label = { Text(sh) }
                    )
                }
                MIN_SALARY_FILTERS.forEach { (valMin, label) ->
                    FilterChip(
                        selected = minSalary == valMin,
                        onClick = { minSalary = valMin },
                        label = { Text(label) }
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!errorMsg.isNullOrBlank()) {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredJobs, key = { it.id }) { job ->
                    JobCard(
                        job = job,
                        status = statusMap[job.id] ?: FollowStatus.UNREAD,
                        onClick = { onSelectJob(job) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobPost, status: FollowStatus, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = job.salaryText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange
                )
            }

            Text(
                text = "${job.city} · ${job.role} · ${job.shift} · ${job.workDays}",
                style = MaterialTheme.typography.bodySmall,
                color = BrandBlue,
                fontWeight = FontWeight.Medium
            )

            val allTags = (job.machineTags + job.materialTags + job.benefitTags).take(6)
            if (allTags.isNotEmpty()) {
                Text(
                    text = allTags.joinToString("  |  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray
                )
            }

            if (job.repeatCount >= 3) {
                Box(
                    modifier = Modifier
                        .background(WarnBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "高频招人预警：近90天已发 ${job.repeatCount} 次",
                        color = WarnText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${job.company} · ${job.sourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "${job.publishedAt} · ${status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == FollowStatus.UNREAD) Color.Gray else BrandBlue
                )
            }
        }
    }
}
