package com.gdgravure.jobs.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gdgravure.jobs.model.AiDigest
import com.gdgravure.jobs.model.FollowStatus
import com.gdgravure.jobs.model.JobPost

private val CITY_FILTERS = listOf("全部广东", "广佛莞(佛/广/莞)", "佛山", "广州", "东莞", "潮州(庵埠)", "汕头", "深圳", "中山", "惠州", "江门", "揭阳")
private val DATE_FILTERS = listOf(0 to "全部日期", 3 to "近3天", 7 to "近7天", 30 to "近30天")
private val ROLE_FILTERS = listOf("全部岗位", "机长/师傅", "副手/助手", "学徒", "调墨/管理")
private val SHIFT_FILTERS = listOf("不限班次", "长白班", "两班倒")
private val MIN_SALARY_FILTERS = listOf(0 to "不限薪资", 8000 to "≥8K", 10000 to "≥10K", 12000 to "≥12K", 15000 to "≥15K")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobListScreen(
    jobs: List<JobPost>,
    updatedAt: String,
    aiDigest: AiDigest,
    isLoading: Boolean,
    errorMsg: String?,
    statusMap: Map<String, FollowStatus>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onSelectJob: (JobPost) -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) } // 0: 找工作, 1: 跟进记录, 2: 行情统计
    var keyword by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("全部广东") }
    var maxDaysAgo by remember { mutableIntStateOf(0) }
    var selectedRole by remember { mutableStateOf("全部岗位") }
    var selectedShift by remember { mutableStateOf("不限班次") }
    var minSalary by remember { mutableIntStateOf(0) }
    var sortBySalary by remember { mutableStateOf(false) }

    var showCityMenu by remember { mutableStateOf(false) }
    var showRoleMenu by remember { mutableStateOf(false) }
    var showExtraMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }

    val filteredJobs = remember(jobs, keyword, selectedCity, selectedRole, selectedShift, minSalary, maxDaysAgo, sortBySalary, statusMap) {
        val list = jobs.filter { job ->
            val st = statusMap[job.id] ?: FollowStatus.UNREAD
            if (st == FollowStatus.BLOCKED) return@filter false
            if (selectedCity == "广佛莞(佛/广/莞)") {
                if (job.city !in listOf("佛山", "广州", "东莞")) return@filter false
            } else if (selectedCity != "全部广东" && job.city != selectedCity) {
                return@filter false
            }
            if (maxDaysAgo > 0 && job.publishedAt.length >= 10) {
                val cutoff = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(
                    java.util.Date(System.currentTimeMillis() - maxDaysAgo * 86400_000L)
                )
                if (job.publishedAt < cutoff) return@filter false
            }
            if (selectedRole != "全部岗位" && job.role != selectedRole) return@filter false
            if (selectedShift != "不限班次" && job.shift != selectedShift) return@filter false
            if (minSalary > 0 && job.salaryMax < minSalary) return@filter false
            if (keyword.isNotBlank()) {
                val kw = keyword.trim()
                val hay = "${job.title} ${job.company} ${job.rawText} ${job.machineTags.joinToString()} ${job.materialTags.joinToString()}"
                if (!hay.contains(kw, ignoreCase = true)) return@filter false
            }
            true
        }
        if (sortBySalary) list.sortedByDescending { it.salaryMax } else list.sortedByDescending { it.publishedAt }
    }

    val trackedJobs = remember(jobs, statusMap) {
        jobs.filter { (statusMap[it.id] ?: FollowStatus.UNREAD) != FollowStatus.UNREAD }
    }

    Scaffold(
        containerColor = SurfaceGray,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Work, contentDescription = null) },
                    label = { Text("找工作") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    label = { Text("跟进(${trackedJobs.size})") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("行情统计") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = onOpenImport,
                    containerColor = BrandBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("粘贴导入", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 顶部工业蓝渐变头部
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(HeaderDarkBlue, HeaderMidBlue)))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "粤印求职 · 广东塑料袋凹印",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "最近同步：$updatedAt  ·  共 ${jobs.size} 条",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Row {
                            IconButton(onClick = onRefresh, enabled = !isLoading) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
                            }
                        }
                    }

                    if (currentTab == 0) {
                        // 紧凑圆角搜索条（绝不换行）
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (keyword.isEmpty()) {
                                        Text(
                                            "搜机型(电子轴/北人)、膜材、镇区...",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = keyword,
                                        onValueChange = { keyword = it },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (keyword.isNotEmpty()) {
                                    IconButton(onClick = { keyword = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "清空", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentOrange)
            }

            if (!errorMsg.isNullOrBlank()) {
                Surface(color = WarnBg, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = errorMsg,
                        color = WarnText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            when (currentTab) {
                0 -> {
                    // 类似 BOSS 直聘的三栏清爽筛选条
                    Surface(color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. 城市下拉
                            Box {
                                FilterDropdownButton(
                                    label = selectedCity,
                                    active = selectedCity != "全部广东",
                                    onClick = { showCityMenu = true }
                                )
                                DropdownMenu(expanded = showCityMenu, onDismissRequest = { showCityMenu = false }) {
                                    CITY_FILTERS.forEach { city ->
                                        DropdownMenuItem(
                                            text = { Text(city, fontWeight = if (city == selectedCity) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedCity = city
                                                showCityMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 2. 岗位下拉
                            Box {
                                FilterDropdownButton(
                                    label = selectedRole,
                                    active = selectedRole != "全部岗位",
                                    onClick = { showRoleMenu = true }
                                )
                                DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                                    ROLE_FILTERS.forEach { role ->
                                        DropdownMenuItem(
                                            text = { Text(role, fontWeight = if (role == selectedRole) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedRole = role
                                                showRoleMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // 3. 薪资与班次下拉
                            Box {
                                val extraLabel = buildString {
                                    if (minSalary > 0) append("≥${minSalary / 1000}K")
                                    if (selectedShift != "不限班次") {
                                        if (isNotEmpty()) append("·")
                                        append(selectedShift)
                                    }
                                    if (isEmpty()) append("薪资/班次")
                                }
                                FilterDropdownButton(
                                    label = extraLabel,
                                    active = minSalary > 0 || selectedShift != "不限班次",
                                    onClick = { showExtraMenu = true }
                                )
                                DropdownMenu(expanded = showExtraMenu, onDismissRequest = { showExtraMenu = false }) {
                                    SHIFT_FILTERS.forEach { sh ->
                                        DropdownMenuItem(
                                            text = { Text("班次：$sh", color = if (sh == selectedShift) BrandBlue else TextPrimary) },
                                            onClick = { selectedShift = sh; showExtraMenu = false }
                                        )
                                    }

                                    HorizontalDivider()
                                    MIN_SALARY_FILTERS.forEach { (sal, lbl) ->
                                        DropdownMenuItem(
                                            text = { Text("薪资：$lbl", color = if (sal == minSalary) BrandBlue else TextPrimary) },
                                            onClick = { minSalary = sal; showExtraMenu = false }
                                        )
                                    }
                                }
                            }

                            // 4. 独立日期筛选按钮（直接可见）
                            Box {
                                val dateLabel = DATE_FILTERS.firstOrNull { it.first == maxDaysAgo }?.second ?: "全部日期"
                                FilterDropdownButton(
                                    label = dateLabel,
                                    active = maxDaysAgo > 0,
                                    onClick = { showDateMenu = true }
                                )
                                DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                                    DATE_FILTERS.forEach { (days, lbl) ->
                                        DropdownMenuItem(
                                            text = { Text(lbl, fontWeight = if (days == maxDaysAgo) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = { maxDaysAgo = days; showDateMenu = false }
                                        )
                                    }
                                }
                            }

                            // 5. 排序切换
                            TextButton(
                                onClick = { sortBySalary = !sortBySalary },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(
                                    if (sortBySalary) "高薪优先" else "最新优先",
                                    fontSize = 12.sp,
                                    color = if (sortBySalary) AccentOrange else TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (filteredJobs.isEmpty()) {
                        EmptyStateView(
                            title = "暂无符合条件的岗位",
                            subtitle = "点击右上角刷新同步最新数据，或点右下角“粘贴导入”存入大平台/微信群招聘。"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (aiDigest.summary.isNotBlank()) {
                                item(key = "ai_daily_digest") {
                                    var expanded by remember { mutableStateOf(true) }
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF5FF)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded }
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("今日有价值岗位 · ${aiDigest.mode}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BrandBlue)
                                                }
                                                Text(if (expanded) "收起" else "展开", fontSize = 12.sp, color = TextSecondary)
                                            }
                                            if (expanded) {
                                                Text(aiDigest.summary, fontSize = 12.sp, color = TextPrimary, lineHeight = 19.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            items(filteredJobs, key = { it.id }) { job ->
                                PolishedJobCard(
                                    job = job,
                                    status = statusMap[job.id] ?: FollowStatus.UNREAD,
                                    onClick = { onSelectJob(job) }
                                )
                            }
                            item { Spacer(Modifier.height(64.dp)) }
                        }
                    }
                }

                1 -> {
                    if (trackedJobs.isEmpty()) {
                        EmptyStateView(
                            title = "还没有跟进记录",
                            subtitle = "点开任意岗位标记“已打电话 / 已约试机 / 避坑屏蔽”，就会自动归档到这里。"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(trackedJobs, key = { it.id }) { job ->
                                PolishedJobCard(
                                    job = job,
                                    status = statusMap[job.id] ?: FollowStatus.UNREAD,
                                    onClick = { onSelectJob(job) }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    MarketStatsView(jobs = jobs)
                }
            }
        }
    }
}

@Composable
private fun FilterDropdownButton(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) BrandBlue else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = if (active) BrandBlue else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolishedJobCard(job: JobPost, status: FollowStatus, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 第一行：标题 + 醒目薪资
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = job.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = job.salaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentOrange
                )
            }

            // 第二行：彩色胶囊标签（自动隐藏“未知”，绝不显示丑陋占位）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TagPill(text = job.city, bg = BrandBlueSoft, fg = BrandBlue)
                TagPill(text = job.role, bg = Color(0xFFF1F5F9), fg = TextPrimary)
                if (job.shift != "未知") {
                    TagPill(
                        text = job.shift,
                        bg = if (job.shift == "长白班") SuccessGreenSoft else AccentOrangeSoft,
                        fg = if (job.shift == "长白班") SuccessGreen else AccentOrange
                    )
                }
                if (job.workDays != "未知") {
                    TagPill(text = job.workDays, bg = Color(0xFFF1F5F9), fg = TextSecondary)
                }
                (job.machineTags + job.materialTags + job.benefitTags).take(4).forEach { tag ->
                    TagPill(text = tag, bg = Color(0xFFF1F5F9), fg = TextSecondary)
                }
            }

            // 高频招人预警
            if (job.repeatCount >= 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(WarnBg)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarnText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "高频招人预警：该电话近90天已发 ${job.repeatCount} 次",
                        color = WarnText,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // 底部：公司/来源/日期（单行防挤换行） + 一键拨号快捷按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.company,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${job.sourceName} · ${job.publishedAt}" + if (status != FollowStatus.UNREAD) " · ${status.label}" else "",
                        fontSize = 11.sp,
                        color = if (status != FollowStatus.UNREAD) BrandBlue else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (job.phone.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.phone}"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("拨号", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyStateView(title: String, subtitle: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Inbox, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun MarketStatsView(jobs: List<JobPost>) {
    val byCity = remember(jobs) {
        jobs.groupingBy { it.city }.eachCount().entries.sortedByDescending { it.value }
    }
    val byRole = remember(jobs) {
        jobs.groupBy { it.role }.map { (role, list) ->
            val priced = list.filter { it.salaryMax > 0 }
            val avg = if (priced.isNotEmpty()) priced.map { (it.salaryMin + it.salaryMax) / 2 }.average().toInt() else 0
            Triple(role, list.size, avg)
        }.sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("按岗位统计（当前已同步 ${jobs.size} 条）", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    byRole.forEach { (role, count, avg) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$role（$count 条）", fontSize = 14.sp, color = TextPrimary)
                            Text(
                                if (avg > 0) "均薪约 ¥$avg/月" else "多为面议",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentOrange
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("广东各产区在招分布", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    byCity.forEach { (city, count) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(city, fontSize = 14.sp, color = TextPrimary)
                            Text("$count 条", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                        }
                    }
                }
            }
        }
    }
}
