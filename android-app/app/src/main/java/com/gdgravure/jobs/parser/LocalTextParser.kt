package com.gdgravure.jobs.parser

import com.gdgravure.jobs.model.JobPost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 安卓端本地即时解析器：
 * 当用户从 BOSS 直聘 / 58 同城 / 微信群复制或“分享”一段广东塑料袋凹印招聘文字进来时，
 * 立即提取城市、岗位、薪资、班次、月休、机型、福利、电话，生成标准卡片。
 */
object LocalTextParser {

    private val gdCityMap = linkedMapOf(
        "潮州(庵埠)" to listOf("潮州", "庵埠", "潮安", "彩塘", "东凤", "枫溪"),
        "汕头" to listOf("汕头", "潮南", "潮阳", "峡山", "澄海"),
        "东莞" to listOf("东莞", "长安", "厚街", "虎门", "塘厦", "凤岗", "常平", "寮步", "大朗", "清溪", "沙田"),
        "佛山" to listOf("佛山", "顺德", "南海", "禅城", "三水", "高明", "勒流", "杏坛"),
        "广州" to listOf("广州", "白云", "增城", "花都", "番禺", "黄埔", "南沙"),
        "深圳" to listOf("深圳", "宝安", "龙岗", "龙华", "光明", "坪山", "沙井", "松岗"),
        "中山" to listOf("中山", "小榄", "古镇", "坦洲", "三乡", "黄圃"),
        "惠州" to listOf("惠州", "惠阳", "博罗", "惠东", "大亚湾", "仲恺"),
        "江门" to listOf("江门", "鹤山", "新会", "开平", "台山"),
        "揭阳" to listOf("揭阳", "普宁", "揭东")
    )

    fun parsePastedText(raw: String): JobPost {
        val text = raw.trim()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(42) ?: "手动导入凹印岗位"

        val city = gdCityMap.entries.firstOrNull { (_, kws) ->
            kws.any { text.contains(it) }
        }?.key ?: "广东(未细分)"

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
            .find(text)?.groupValues?.get(1) ?: "手动导入企业"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

        return JobPost(
            id = "manual_${UUID.randomUUID().toString().take(8)}",
            title = firstLine,
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
            rawText = text,
            sourceName = "手动导入",
            sourceUrl = "",
            publishedAt = today,
            repeatCount = 1,
            isManualImport = true
        )
    }

    private fun parseSalary(text: String): Triple<String, Int, Int> {
        Regex("(\\d+(?:\\.\\d+)?)\\s*[-~～至到]\\s*(\\d+(?:\\.\\d+)?)\\s*万").find(text)?.let {
            val lo = (it.groupValues[1].toDouble() * 10000).toInt()
            val hi = (it.groupValues[2].toDouble() * 10000).toInt()
            return Triple("$lo-${hi}元", lo, hi)
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*[kK千]?\\s*[-~～至到]\\s*(\\d+(?:\\.\\d+)?)\\s*[kK千]").find(text)?.let {
            val loRaw = it.groupValues[1].toDouble()
            val hiRaw = it.groupValues[2].toDouble()
            val lo = if (loRaw < 100) (loRaw * 1000).toInt() else loRaw.toInt()
            val hi = if (hiRaw < 100) (hiRaw * 1000).toInt() else hiRaw.toInt()
            if (lo in 2000..60000 && hi >= lo) return Triple("$lo-${hi}元", lo, hi)
        }
        Regex("(\\d{4,5})\\s*[-~～至到]\\s*(\\d{4,5})").find(text)?.let {
            val lo = it.groupValues[1].toInt()
            val hi = it.groupValues[2].toInt()
            if (lo in 2500..60000 && hi >= lo) return Triple("$lo-${hi}元", lo, hi)
        }
        return Triple("面议", 0, 0)
    }
}
