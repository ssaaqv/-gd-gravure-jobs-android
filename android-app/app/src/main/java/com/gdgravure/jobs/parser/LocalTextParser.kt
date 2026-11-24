package com.gdgravure.jobs.parser

import com.gdgravure.jobs.model.AiDigest
import com.gdgravure.jobs.model.JobPost
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object LocalTextParser {

    private val gdCityMap = linkedMapOf(
        "潮州(庵埠)" to listOf("潮州", "庵埠", "潮安", "彩塘", "东凤", "枫溪", "饶平"),
        "汕头" to listOf("汕头", "潮南", "潮阳", "峡山", "澄海", "龙湖", "金平"),
        "东莞" to listOf("东莞", "长安", "厚街", "虎门", "塘厦", "凤岗", "常平", "寮步", "大朗", "清溪", "黄江", "桥头", "沙田"),
        "佛山" to listOf("佛山", "顺德", "南海", "禅城", "三水", "高明", "勒流", "杏坛", "容桂", "大沥"),
        "广州" to listOf("广州", "白云", "增城", "花都", "番禺", "黄埔", "南沙", "从化"),
        "深圳" to listOf("深圳", "宝安", "龙岗", "龙华", "光明", "坪山", "沙井", "松岗", "公明"),
        "中山" to listOf("中山", "小榄", "古镇", "坦洲", "三乡", "黄圃", "南头", "东升"),
        "惠州" to listOf("惠州", "惠阳", "博罗", "惠东", "大亚湾", "仲恺"),
        "江门" to listOf("江门", "鹤山", "新会", "开平", "台山"),
        "揭阳" to listOf("揭阳", "普宁", "揭东", "榕城"),
        "珠海" to listOf("珠海", "斗门", "金湾"),
        "肇庆" to listOf("肇庆", "四会", "高要")
    )

    private val nonGdProvinces = listOf(
        "浙江", "杭州", "温州", "龙港", "苍南", "台州", "黄岩", "宁波", "义乌",
        "江苏", "苏州", "昆山", "无锡", "常州", "南京", "南通", "扬州",
        "山东", "青岛", "临沂", "潍坊", "济南", "河北", "雄县", "保定",
        "福建", "泉州", "晋江", "厦门", "福州", "河南", "湖北", "武汉",
        "湖南", "长沙", "四川", "成都", "重庆", "安徽", "桐城", "上海", "北京"
    )

    private val overseasExclude = listOf("越南", "柬埔寨", "缅甸", "泰国", "马来西亚", "印尼", "非洲", "出国", "外派")

    private val strongKeywords = listOf(
        "凹版", "凹印", "塑料印刷", "软包装", "彩印", "电子轴", "机械轴",
        "表印", "里印", "塑料袋", "包装袋", "背心袋", "复合袋", "自立袋", "卫包"
    )
    private val mediumKeywords = listOf(
        "印刷机长", "印刷师傅", "印刷副手", "印刷一助", "印刷二助", "印刷助手", "印刷学徒",
        "北人", "欧诺", "华丰", "方邦", "博斯特", "松德", "PE", "OPP", "BOPP", "PET", "CPP", "PVC", "筒膜", "调墨"
    )
    private val seekerExclude = listOf("求职", "本人找工作", "找印刷工作", "求职印刷", "求职凹印")
    private val officeExclude = listOf("业务跟单", "销售", "业务员", "财务", "会计", "文员", "内勤", "司机", "保安", "仓管")
    private val nonGravureOnly = listOf("丝印", "移印", "UV平板", "数码打印", "海德堡胶印", "瓦楞纸箱")

    fun parseScrapedPost(
        title: String,
        body: String,
        locationHint: String,
        sourceName: String,
        sourceUrl: String,
        publishedAt: String
    ): JobPost? {
        if (seekerExclude.any { title.contains(it) }) return null
        if (officeExclude.any { title.contains(it) } && !listOf("凹印", "凹版", "印刷").any { title.contains(it) }) return null
        val combined = "$title $body"
        if (overseasExclude.any { title.contains(it) || body.take(120).contains(it) }) return null

        val hasStrong = strongKeywords.any { combined.contains(it, ignoreCase = true) }
        val hasNonGravure = nonGravureOnly.any { combined.contains(it, ignoreCase = true) }
        if (hasNonGravure && !hasStrong) return null

        var score = 0
        strongKeywords.forEach { if (combined.contains(it, ignoreCase = true)) score += 25 }
        mediumKeywords.forEach { if (combined.contains(it, ignoreCase = true)) score += 12 }
        if (listOf("凹印", "凹版", "印刷机长", "印刷师傅", "印刷副手", "印刷学徒").any { title.contains(it) }) score += 30
        if (score < 20) return null

        val city = detectGdCity(combined, locationHint) ?: return null
        return buildJobPost(
            id = md5("$sourceUrl|$title|$city").take(12),
            title = title.trim().take(80),
            raw = body.trim().take(900),
            city = city,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            publishedAt = normalizeDate(publishedAt.ifBlank { extractPublishDate(combined) }),
            isManual = false
        )
    }

    fun parsePastedText(raw: String): JobPost {
        val text = raw.trim()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(42) ?: "手动导入凹印岗位"
        val city = detectGdCity(text, "") ?: "广东(未细分)"
        return buildJobPost(
            id = "manual_${UUID.randomUUID().toString().take(8)}",
            title = firstLine,
            raw = text,
            city = city,
            sourceName = "手动导入",
            sourceUrl = "",
            publishedAt = todayStr(),
            isManual = true
        )
    }

    fun buildLocalDigest(jobs: List<JobPost>): AiDigest {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        if (jobs.isEmpty()) {
            return AiDigest(now, "本地直抓精选", "暂未抓到广东凹印新帖，点右上角刷新即可直连国内站点抓取。")
        }
        val ranked = jobs.sortedByDescending { scoreForDigest(it) }
        val top = ranked.take(3)
        val lines = mutableListOf("【今日最值得联系（手机本地实时分析）】")
        top.forEachIndexed { index, j ->
            val tags = (j.machineTags + j.benefitTags).take(3).joinToString(" / ")
            val extra = if (tags.isNotBlank()) "（$tags）" else ""
            val salClean = j.salaryText.removeSuffix("元")
            val salPart = if (j.salaryText != "面议" && !j.title.contains(salClean)) " · ${j.salaryText}" else ""
            val tel = if (j.phone.isNotBlank()) " 电话:${j.phone}" else ""
            lines.add("${index + 1}. [${j.city}] ${j.title}$salPart$extra$tel")
        }
        val warns = jobs.count { it.repeatCount >= 3 }
        if (warns > 0) {
            lines.add("避坑提醒：有 $warns 条岗位近期反复招人，打电话时注意核实车间环境与发薪日。")
        }
        return AiDigest(now, "本地直抓精选", lines.joinToString("\n"))
    }

    private fun scoreForDigest(j: JobPost): Int {
        var s = 0
        if (j.salaryMax >= 15000) s += 35
        else if (j.salaryMax >= 12000) s += 25
        else if (j.salaryMax >= 8000) s += 15
        if (j.shift == "长白班") s += 12
        if ("包吃住" in j.benefitTags) s += 10
        if ("交社保" in j.benefitTags) s += 10
        if (j.phone.isNotBlank()) s += 8
        if (j.repeatCount >= 3) s -= 20
        return s
    }

    private fun detectGdCity(text: String, hint: String): String? {
        gdCityMap.entries.firstOrNull { (_, kws) -> kws.any { hint.contains(it) } }?.let { return it.key }
        if (nonGdProvinces.any { hint.contains(it) }) return null
        gdCityMap.entries.firstOrNull { (_, kws) -> kws.any { text.contains(it) } }?.let { return it.key }
        if (text.contains("广东") || text.contains("珠三角") || text.contains("粤东")) return "广东(未细分)"
        return null
    }

    private fun buildJobPost(
        id: String,
        title: String,
        raw: String,
        city: String,
        sourceName: String,
        sourceUrl: String,
        publishedAt: String,
        isManual: Boolean
    ): JobPost {
        val text = "$title\n$raw"
        val role = when {
            listOf("机长", "开机师傅", "印刷师傅", "主操").any { text.contains(it) } -> "机长/师傅"
            listOf("一助", "副手", "二助", "助手", "二手").any { text.contains(it) } -> "副手/助手"
            listOf("学徒", "普工").any { text.contains(it) } -> "学徒"
            listOf("车间主任", "调墨", "主管").any { text.contains(it) } -> "调墨/管理"
            else -> "凹印相关"
        }
        val (salaryText, salaryMin, salaryMax) = parseSalary(text)
        val salaryType = when {
            text.contains("保底") && text.contains("计件") -> "保底+计件"
            text.contains("计件") -> "计件"
            salaryMin > 0 -> "固定月薪"
            else -> "面议"
        }
        val shift = when {
            listOf("两班倒", "白夜班", "倒班", "12小时", "夜班").any { text.contains(it) } -> "两班倒"
            listOf("长白班", "早八晚六", "早8晚6", "白班").any { text.contains(it) } -> "长白班"
            else -> "未知"
        }
        val workDays = Regex("月休\\s*([一二三四两\\d]+)\\s*天").find(text)?.let { "月休${it.groupValues[1]}天" }
            ?: Regex("(2[4-9]|30)\\s*天").find(text)?.let { "${it.groupValues[1]}天制" }
            ?: when {
                text.contains("单休") -> "单休(月休4天)"
                text.contains("双休") -> "双休"
                else -> "未知"
            }

        val machines = buildList {
            if (text.contains("电子轴")) add("电子轴")
            if (text.contains("机械轴")) add("机械轴")
            if (text.contains("北人")) add("陕西北人")
            if (text.contains("欧诺")) add("欧诺")
            if (text.contains("华丰")) add("华丰")
            if (text.contains("方邦")) add("方邦")
            if (text.contains("博斯特")) add("博斯特")
            Regex("(\\d{1,2})\\s*色").find(text)?.let { add("${it.groupValues[1]}色机") }
        }
        val materials = listOf("PE", "OPP", "BOPP", "PET", "CPP", "PVC", "表印", "里印", "复合袋", "背心袋", "自立袋")
            .filter { text.contains(it, ignoreCase = true) }
        val benefits = buildList {
            if (listOf("包吃住", "包吃包住", "包食宿", "食宿全包").any { text.contains(it) }) add("包吃住")
            else if (text.contains("包吃")) add("包吃")
            if (listOf("五险", "社保", "医保").any { text.contains(it) }) add("交社保")
            if (text.contains("年终奖")) add("年终奖")
        }
        val phone = Regex("(?<!\\d)(1[3-9]\\d{9})(?!\\d)").find(text)?.groupValues?.get(1) ?: ""
        val company = Regex("([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:科技有限公司|包装有限公司|彩印有限公司|印刷有限公司|彩印厂|印刷厂|包装厂))")
            .find(text)?.groupValues?.get(1) ?: "广东软包装企业"

        return JobPost(
            id = id,
            title = title,
            company = company,
            city = city,
            role = role,
            salaryText = salaryText,
            salaryMin = salaryMin,
            salaryMax = salaryMax,
            salaryType = salaryType,
            shift = shift,
            workDays = workDays,
            machineTags = machines,
            materialTags = materials,
            benefitTags = benefits,
            phone = phone,
            wechat = if (text.contains("微信同号")) phone else "",
            contactName = "",
            rawText = raw,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            publishedAt = publishedAt,
            repeatCount = 1,
            isManualImport = isManual
        )
    }

    private fun parseSalary(rawText: String): Triple<String, Int, Int> {
        // 先剔除 11 位手机号、座机号和 6 位以上 QQ/数字串，彻底杜绝把手机号前5位当成工资
        val text = rawText
            .replace(Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"), " ")
            .replace(Regex("(?<!\\d)\\d{6,}(?!\\d)"), " ")

        Regex("(\\d+(?:\\.\\d+)?)\\s*[-~～至到]\\s*(\\d+(?:\\.\\d+)?)\\s*万").find(text)?.let {
            val lo = (it.groupValues[1].toDouble() * 10000).toInt()
            val hi = (it.groupValues[2].toDouble() * 10000).toInt()
            if (lo in 3000..60000 && hi >= lo) return Triple("$lo-${hi}元", lo, hi)
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*[kK千]?\\s*[-~～至到]\\s*(\\d+(?:\\.\\d+)?)\\s*[kK千]").find(text)?.let {
            val loRaw = it.groupValues[1].toDouble()
            val hiRaw = it.groupValues[2].toDouble()
            val lo = if (loRaw < 100) (loRaw * 1000).toInt() else loRaw.toInt()
            val hi = if (hiRaw < 100) (hiRaw * 1000).toInt() else hiRaw.toInt()
            if (lo in 2000..60000 && hi >= lo) return Triple("$lo-${hi}元", lo, hi)
        }
        Regex("(?<!\\d)(\\d{4,5})\\s*[-~～至到]\\s*(\\d{4,5})(?!\\d)").find(text)?.let {
            val lo = it.groupValues[1].toInt()
            val hi = it.groupValues[2].toInt()
            if (lo in 2500..60000 && hi >= lo) return Triple("$lo-${hi}元", lo, hi)
        }
        Regex("(?:月薪|工资|待遇|薪资|保底)[^\\d]{0,6}(\\d{4,5})(?!\\d)").find(text)?.let {
            val v = it.groupValues[1].toInt()
            if (v in 2500..60000) return Triple("约${v}元", v, v)
        }
        return Triple("面议", 0, 0)
    }

    fun normalizeDate(raw: String): String {
        val m = Regex("(202[4-7])[/-](\\d{1,2})[/-](\\d{1,2})").find(raw) ?: return raw
        return "${m.groupValues[1]}-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')}"
    }

    fun extractPublishDate(text: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        Regex("(202[4-7])[/-](\\d{1,2})[/-](\\d{1,2})").find(text)?.let {
            val y = it.groupValues[1]
            val m = it.groupValues[2].padStart(2, '0')
            val d = it.groupValues[3].padStart(2, '0')
            return "$y-$m-$d"
        }
        val cal = Calendar.getInstance()
        if (text.contains("今天") || text.contains("今日") || text.contains("刚刚") || text.contains("小时前")) {
            return fmt.format(cal.time)
        }
        if (text.contains("昨天") || text.contains("昨日")) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return fmt.format(cal.time)
        }
        if (text.contains("前天")) {
            cal.add(Calendar.DAY_OF_YEAR, -2)
            return fmt.format(cal.time)
        }
        Regex("(\\d{1,2})\\s*天前").find(text)?.let {
            val days = it.groupValues[1].toIntOrNull()?.coerceIn(1, 60) ?: 1
            cal.add(Calendar.DAY_OF_YEAR, -days)
            return fmt.format(cal.time)
        }
        Regex("(?<!\\d)(0?[1-9]|1[0-2])-([0-2]?[0-9]|3[01])(?!\\d)").find(text)?.let {
            val y = cal.get(Calendar.YEAR)
            val m = it.groupValues[1].padStart(2, '0')
            val d = it.groupValues[2].padStart(2, '0')
            return "$y-$m-$d"
        }
        return fmt.format(cal.time)
    }

    private fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
