package com.gdgravure.jobs.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gdgravure.jobs.model.JobPost
import com.gdgravure.jobs.parser.LocalTextParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * 手机本地隐身 WebView 渲染抓取器：
 * 像正常手机浏览器一样打开 JS 动态加载的广东制造业招聘页（如智通直聘印刷包装频道），
 * 等待页面渲染完成后提取公开岗位文字与链接。
 */
object WebViewScraper {

    private val dynamicTargets = listOf(
        Triple("智通直聘", "https://www.job5156.com/ids_pap/", "广东")
    )

    suspend fun scrapeDynamicSites(context: Context): Pair<List<JobPost>, Map<String, Int>> {
        val allPosts = mutableListOf<JobPost>()
        val stats = linkedMapOf<String, Int>()

        for ((name, url, locHint) in dynamicTargets) {
            val items = withTimeoutOrNull(14_000L) {
                fetchRenderedLinks(context.applicationContext, url)
            }.orEmpty()

            val parsed = items.mapNotNull { (title, body, href) ->
                LocalTextParser.parseScrapedPost(
                    title = title,
                    body = body,
                    locationHint = locHint,
                    sourceName = name,
                    sourceUrl = href.ifBlank { url },
                    publishedAt = ""
                )
            }.distinctBy { it.id }

            stats[name] = parsed.size
            allPosts.addAll(parsed)
        }
        return allPosts to stats
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchRenderedLinks(
        context: Context,
        targetUrl: String
    ): List<Triple<String, String, String>> = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            var finished = false
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
            }

            fun finishOnce(result: List<Triple<String, String, String>>) {
                if (!finished) {
                    finished = true
                    runCatching {
                        webView.stopLoading()
                        webView.destroy()
                    }
                    if (cont.isActive) cont.resume(result)
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    mainHandler.postDelayed({
                        if (finished) return@postDelayed
                        val js = """
                            (function() {
                              var out = [];
                              var nodes = document.querySelectorAll('a, li, div');
                              var seen = {};
                              for (var i = 0; i < nodes.length && out.length < 40; i++) {
                                var txt = (nodes[i].innerText || '').replace(/\s+/g, ' ').trim();
                                if (txt.length >= 16 && txt.length <= 280 &&
                                    (txt.indexOf('凹印') >= 0 || txt.indexOf('凹版') >= 0 || txt.indexOf('印刷') >= 0 || txt.indexOf('彩印') >= 0 || txt.indexOf('软包装') >= 0)) {
                                  var a = nodes[i].tagName === 'A' ? nodes[i] : nodes[i].querySelector('a[href]');
                                  var href = a ? a.href : location.href;
                                  if (!seen[txt.slice(0, 30)]) {
                                    seen[txt.slice(0, 30)] = true;
                                    var title = a && a.innerText.trim().length >= 3 ? a.innerText.trim() : txt.slice(0, 36);
                                    out.push([title, txt, href]);
                                  }
                                }
                              }
                              return JSON.stringify(out);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js) { rawJson ->
                            val parsed = runCatching {
                                val unquoted = if (rawJson != null && rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                                    org.json.JSONTokener(rawJson).nextValue() as String
                                } else {
                                    rawJson ?: "[]"
                                }
                                val arr = JSONArray(unquoted)
                                (0 until arr.length()).mapNotNull { idx ->
                                    val row = arr.optJSONArray(idx) ?: return@mapNotNull null
                                    Triple(row.optString(0), row.optString(1), row.optString(2))
                                }
                            }.getOrDefault(emptyList())
                            finishOnce(parsed)
                        }
                    }, 3200L)
                }
            }

            cont.invokeOnCancellation {
                mainHandler.post { finishOnce(emptyList()) }
            }
            webView.loadUrl(targetUrl)
        }
    }
}
