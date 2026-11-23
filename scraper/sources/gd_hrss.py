"""广东公共求职招聘服务平台（省人社官方）动态采集模块"""
from __future__ import annotations

from datetime import datetime
from typing import List

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record

URL = "https://ggfw.hrss.gd.gov.cn/recruitment/internet/main/"


def scrape(max_items: int = 15) -> List[JobPost]:
    results: List[JobPost] = []
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        return results

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(locale="zh-CN")
            page.goto(URL, wait_until="domcontentloaded", timeout=25000)
            page.wait_for_timeout(3500)
            html = page.content()
            browser.close()

        soup = BeautifulSoup(html, "lxml")
        for blk in soup.find_all(["div", "li"]):
            txt = blk.get_text(" ", strip=True)
            if 25 <= len(txt) <= 400 and any(k in txt for k in ("凹印", "凹版", "彩印", "软包装", "印刷")):
                post = parse_job_record(
                    title=txt[:36],
                    body=txt,
                    location_hint="广东",
                    source_name="广东人社官方",
                    source_url=URL,
                    published_at=datetime.now().strftime("%Y-%m-%d"),
                )
                if post:
                    results.append(post)
                    if len(results) >= max_items:
                        break
    except Exception as exc:
        print(f"[gd_hrss][WARN] error: {exc}")

    return results
