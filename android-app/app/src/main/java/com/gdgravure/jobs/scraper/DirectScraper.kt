package com.gdgravure.jobs.scraper

import com.gdgravure.jobs.model.JobPost
import com.gdgravure.jobs.parser.LocalTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 中国本地 7 路并发直抓引擎（纯手机国内网络直连，零服务器成本）：
 * 1. 91软包装人才网 (91rbz.com - 广东塑料软包装/凹印主力)
 * 2. 天下软包装 (txrbz.com - 广东10大产区)
 * 3. 中国印刷人才网 (m.pjob.net)
 * 4. 潮州人才网 (m.czzp.cn - 潮州/庵埠本地厂)
 * 5. 51job普工印刷频道 (mpugong.51job.com - 东莞/潮州/佛山/广州/汕头)
 * 6. 联展资讯网 (lzzxcn.com)
 * 7. 潮南人才网 (cnqrc.com)
 */
object DirectScraper {

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"

    private val txrbzListUrls = listOf(
        "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%B6%AB",  // 广东
        "http://www.txrbz.com/Search.asp?KeyWord=%E2%D6%B2%BA",  // 庵埠
        "http://www.txrbz.com/Search.asp?KeyWord=%B3%B1%D6%DD",  // 潮州
        "http://www.txrbz.com/Search.asp?KeyWord=%C9%C7%CD%B7",  // 汕头
        "http://www.txrbz.com/Search.asp?KeyWord=%B6%AB%DD%B8",  // 东莞
        "http://www.txrbz.com/Search.asp?KeyWord=%B7%F0%C9%BD",  // 佛山
        "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%D6%DD",  // 广州
        "http://www.txrbz.com/Search.asp?KeyWord=%C9%EE%DB%DA",  // 深圳
        "http://www.txrbz.com/Search.asp?KeyWord=%D6%D0%C9%BD",  // 中山
        "http://www.txrbz.com/Search.asp?KeyWord=%BB%DD%D6%DD"   // 惠州
    )

    private val rbz91Urls = listOf(
        "https://www.91rbz.com/job/list/1066-20-0_-1_0_0_0_0_1_0_0-0-0-0-1.html",
        "https://www.91rbz.com/job/list/0-20-0_-1_0_0_0_0_1_0_0-0-0-0-1.html"
    )

    private val pjobUrls = listOf(
        "https://m.pjob.net/",
        "https://m.pjob.net/jobs/?keyword=%E5%87%B9%E5%8D%B0",
        "https://m.pjob.net/jobs/?keyword=%E8%BD%AF%E5%8C%85%E8%A3%85"
    )

    private val czzpUrls = listOf(
        "http://m.czzp.cn/list/job.aspx?kw=1&keyword=%E5%8D%B0%E5%88%B7&jcity1Hidden=101800",
        "http://m.czzp.cn/list/job.aspx?kw=1&keyword=%E5%8C%85%E8%A3%85&jcity1Hidden=101800"
    )

    private val pugong51Urls = listOf(
        "https://mpugong.51job.com/dongguan/yinshuagong/",
        "https://mpugong.51job.com/chaozhou/yinshuagong/",
        "https://mpugong.51job.com/foshan/yinshuagong/",
        "https://mpugong.51job.com/guangzhou/yinshuagong/",
        "https://mpugong.51job.com/shantou/yinshuagong/"
    )

    suspend fun scrapeAllDomestic(): List<JobPost> = withContext(Dispatchers.IO) {
        coroutineScope {
            val tasks = listOf(
                async { scrape91Rbz() },
                async { scrapeTxrbz() },
                async { scrapePjob() },
                async { scrapeCzzp() },
                async { scrape51Pugong() },
                async { scrapeLzzxcn() },
                async { scrapeCnqrc() }
            )
            tasks.awaitAll().flatten().distinctBy { it.id }
        }
    }

    private fun scrape91Rbz(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        val seen = mutableSetOf<String>()
        for (url in rbz91Urls) {
            runCatching {
                val doc = Jsoup.connect(url).userAgent(UA).timeout(12000).get()
                doc.select("a[href*=/job/]").forEach { a ->
                    val href = a.absUrl("href")
                    val title = a.text().trim()
                    if (href.contains("/job/") && !href.contains("/list/") && title.length >= 3 && seen.add(href)) {
                        if (out.size >= 25) return@forEach
                        val parentTxt = a.parent()?.parent()?.text()?.trim().orEmpty().ifBlank { title }
                        LocalTextParser.parseScrapedPost(
                            title = title,
                            body = "$title $parentTxt",
                            locationHint = "广东",
                            sourceName = "91软包装人才网",
                            sourceUrl = href,
                            publishedAt = ""
                        )?.let { out.add(it) }
                    }
                }
            }
        }
        return out
    }

    private suspend fun scrapeTxrbz(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        val seen = mutableSetOf<String>()
        for (listUrl in txrbzListUrls) {
            runCatching {
                val doc = Jsoup.connect(listUrl).userAgent(UA).timeout(10000).get()
                val links = doc.select("a[href*=/info/]").filter {
                    val href = it.absUrl("href")
                    href.endsWith(".html") && it.text().trim().length >= 4 && seen.add(href)
                }.take(5)

                for (a in links) {
                    delay(300)
                    val detailUrl = a.absUrl("href")
                    val title = a.text().trim()
                    runCatching {
                        val dDoc = Jsoup.connect(detailUrl).userAgent(UA).timeout(10000).get()
                        val fullText = dDoc.text()
                        val date = Regex("发布时间[：:]\\s*(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})")
                            .find(fullText)?.groupValues?.get(1)?.replace("/", "-") ?: ""
                        val loc = Regex("(广东[^\\s]{0,6})").find(fullText)?.groupValues?.get(1) ?: ""
                        val body = Regex("详情[：:](.+?)(?:分享本信息|上一篇|相关留言|$)")
                            .find(fullText)?.groupValues?.get(1)?.trim() ?: fullText.take(650)

                        LocalTextParser.parseScrapedPost(
                            title = title,
                            body = body,
                            locationHint = loc,
                            sourceName = "天下软包装",
                            sourceUrl = detailUrl,
                            publishedAt = date
                        )?.let { out.add(it) }
                    }
                }
            }
        }
        return out
    }

    private suspend fun scrapePjob(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        val seen = mutableSetOf<String>()
        for (url in pjobUrls) {
            runCatching {
                val doc = Jsoup.connect(url).userAgent(UA).timeout(10000).get()
                val links = doc.select("a[href*=/job], a[href*=/company/]").filter {
                    val href = it.absUrl("href")
                    href.endsWith(".html") && it.text().trim().length >= 3 && seen.add(href)
                }.take(8)

                for (a in links) {
                    delay(300)
                    val detailUrl = a.absUrl("href")
                    val title = a.text().trim()
                    runCatching {
                        val dDoc = Jsoup.connect(detailUrl).userAgent(UA).timeout(10000).get()
                        LocalTextParser.parseScrapedPost(
                            title = title,
                            body = dDoc.text().take(900),
                            locationHint = "",
                            sourceName = "中国印刷人才网",
                            sourceUrl = detailUrl,
                            publishedAt = ""
                        )?.let { out.add(it) }
                    }
                }
            }
        }
        return out
    }

    private fun scrapeCzzp(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        for (url in czzpUrls) {
            runCatching {
                val doc = Jsoup.connect(url).userAgent(UA).timeout(10000).get()
                doc.select("li, div.job_item, a").forEach { el ->
                    val txt = el.text().trim()
                    if (txt.length in 16..300 && listOf("凹印", "凹版", "印刷", "彩印", "机长", "副手", "学徒").any { txt.contains(it) }) {
                        val link = if (el.tagName() == "a") el.absUrl("href") else el.selectFirst("a")?.absUrl("href") ?: url
                        LocalTextParser.parseScrapedPost(
                            title = txt.take(36),
                            body = txt,
                            locationHint = "广东潮州",
                            sourceName = "潮州人才网",
                            sourceUrl = link.ifBlank { url },
                            publishedAt = ""
                        )?.let { out.add(it) }
                    }
                }
            }
        }
        return out
    }

    private fun scrape51Pugong(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        for (url in pugong51Urls) {
            runCatching {
                val doc = Jsoup.connect(url).userAgent(UA).timeout(10000).get()
                doc.select("a, li, div").forEach { el ->
                    val txt = el.text().trim()
                    if (txt.length in 20..320 && listOf("凹印", "凹版", "彩印", "软包装", "印刷机长", "印刷工").any { txt.contains(it) }) {
                        val link = if (el.tagName() == "a") el.absUrl("href") else el.selectFirst("a")?.absUrl("href") ?: url
                        LocalTextParser.parseScrapedPost(
                            title = txt.take(38),
                            body = txt,
                            locationHint = "广东",
                            sourceName = "51job普工",
                            sourceUrl = link.ifBlank { url },
                            publishedAt = ""
                        )?.let { out.add(it) }
                    }
                }
            }
        }
        return out
    }

    private fun scrapeLzzxcn(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        runCatching {
            val lzzUrl = "http://www.lzzxcn.com/newslist.asp?nid=20"
            val doc = Jsoup.connect(lzzUrl).userAgent(UA).timeout(10000).get()
            doc.select("li, tr, div").forEach { el ->
                val txt = el.text().trim()
                if (txt.length in 25..420 && listOf("凹版", "凹印", "电子轴", "软包装", "彩印").any { txt.contains(it) }) {
                    val a = el.selectFirst("a[href]")
                    val link = a?.absUrl("href")?.ifBlank { lzzUrl } ?: lzzUrl
                    val title = a?.text()?.trim()?.takeIf { it.length >= 4 } ?: txt.take(32)
                    LocalTextParser.parseScrapedPost(
                        title = title,
                        body = txt,
                        locationHint = "",
                        sourceName = "联展资讯网",
                        sourceUrl = link,
                        publishedAt = ""
                    )?.let { out.add(it) }
                }
            }
        }
        return out
    }

    private fun scrapeCnqrc(): List<JobPost> {
        val out = mutableListOf<JobPost>()
        runCatching {
            val url = "https://www.cnqrc.com/index/company/job/id/2470.html"
            val doc = Jsoup.connect(url).userAgent(UA).timeout(10000).get()
            LocalTextParser.parseScrapedPost(
                title = doc.title().ifBlank { "汕头潮南印刷包装招聘" },
                body = doc.text().take(800),
                locationHint = "广东汕头",
                sourceName = "潮南人才网",
                sourceUrl = url,
                publishedAt = ""
            )?.let { out.add(it) }
        }
        return out
    }
}
