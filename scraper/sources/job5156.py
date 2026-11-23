"""智通直聘 (job5156.com) 印刷/纸品/包装频道 Playwright 动态渲染采集模块"""
from __future__ import annotations

from datetime import datetime
from typing import List
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record

TARGET_URLS = [
    "https://www.job5156.com/ids_pap/",
]


def scrape(max_items: int = 30) -> List[JobPost]:
    results: List[JobPost] = []
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("[job5156][WARN] Playwright not installed, skipping dynamic source.")
        return results

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(locale="zh-CN")
            for url in TARGET_URLS:
                try:
                    page.goto(url, wait_until="domcontentloaded", timeout=25000)
                    page.wait_for_timeout(3000)
                    html = page.content()
                except Exception as exc:
                    print(f"[job5156][WARN] goto failed {url}: {exc}")
                    continue

                soup = BeautifulSoup(html, "lxml")
                seen = set()
                for card in soup.find_all(["li", "div", "article"]):
                    txt = card.get_text(" ", strip=True)
                    if not (20 <= len(txt) <= 400):
                        continue
                    if not any(k in txt for k in ("凹印", "凹版", "印刷机长", "印刷师傅", "印刷副手", "印刷学徒", "软包装", "彩印")):
                        continue
                    a = card.find("a", href=True)
                    link = urljoin(url, a["href"]) if a else url
                    if link in seen:
                        continue
                    seen.add(link)
                    title = a.get_text(" ", strip=True) if a and len(a.get_text(strip=True)) >= 3 else txt[:36]
                    post = parse_job_record(
                        title=title,
                        body=txt,
                        location_hint="广东",
                        source_name="智通直聘",
                        source_url=link,
                        published_at=datetime.now().strftime("%Y-%m-%d"),
                    )
                    if post:
                        results.append(post)
                        if len(results) >= max_items:
                            break
            browser.close()
    except Exception as exc:
        print(f"[job5156][WARN] browser error: {exc}")

    return results
