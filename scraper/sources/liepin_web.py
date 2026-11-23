"""猎聘公开列表页（凹版印刷/印刷包装）广东岗位低频采集模块"""
from __future__ import annotations

from datetime import datetime
from typing import List
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record

URLS = [
    "https://www.liepin.com/zpaobanyinshuagong/",
    "https://www.liepin.com/zpyinshuabaozhuang/",
]


def scrape(max_items: int = 20) -> List[JobPost]:
    results: List[JobPost] = []
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        return results

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(locale="zh-CN")
            for url in URLS:
                try:
                    page.goto(url, wait_until="domcontentloaded", timeout=20000)
                    page.wait_for_timeout(2500)
                    html = page.content()
                except Exception:
                    continue

                soup = BeautifulSoup(html, "lxml")
                seen = set()
                for card in soup.find_all(["div", "li"]):
                    txt = card.get_text(" ", strip=True)
                    if not (25 <= len(txt) <= 360):
                        continue
                    if not any(k in txt for k in ("凹印", "凹版", "彩印", "软包装", "印刷机长")):
                        continue
                    a = card.find("a", href=True)
                    link = urljoin(url, a["href"]) if a else url
                    if link in seen:
                        continue
                    seen.add(link)
                    title = a.get_text(" ", strip=True) if a and len(a.get_text(strip=True)) >= 3 else txt[:34]
                    post = parse_job_record(
                        title=title,
                        body=txt,
                        location_hint="",
                        source_name="猎聘公开页",
                        source_url=link,
                        published_at=datetime.now().strftime("%Y-%m-%d"),
                    )
                    if post:
                        results.append(post)
                        if len(results) >= max_items:
                            break
            browser.close()
    except Exception as exc:
        print(f"[liepin_web][WARN] error: {exc}")

    return results
