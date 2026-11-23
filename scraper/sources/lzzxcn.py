"""联展资讯网 (lzzxcn.com) 包装印刷招聘栏目采集模块"""
from __future__ import annotations

import time
from datetime import datetime
from typing import List
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record
from sources.common import fetch_html

LIST_URL = "http://www.lzzxcn.com/newslist.asp?nid=20"


def scrape(max_items: int = 25) -> List[JobPost]:
    results: List[JobPost] = []
    try:
        html = fetch_html(LIST_URL)
    except Exception as exc:
        print(f"[lzzxcn][WARN] list failed: {exc}")
        return results

    soup = BeautifulSoup(html, "lxml")
    # 既解析列表页直接展示的块，也进入详情链接
    blocks = soup.find_all(["li", "tr", "div"])
    seen_texts = set()
    for blk in blocks:
        txt = blk.get_text(" ", strip=True)
        if 25 <= len(txt) <= 500 and txt not in seen_texts:
            if any(k in txt for k in ("凹版", "凹印", "电子轴", "软包装", "彩印")):
                seen_texts.add(txt)
                a = blk.find("a", href=True)
                link = urljoin(LIST_URL, a["href"]) if a else LIST_URL
                title = a.get_text(" ", strip=True) if a and len(a.get_text(strip=True)) >= 4 else txt[:32]
                post = parse_job_record(
                    title=title,
                    body=txt,
                    location_hint="",
                    source_name="联展资讯网",
                    source_url=link,
                    published_at=datetime.now().strftime("%Y-%m-%d"),
                )
                if post:
                    results.append(post)
                    if len(results) >= max_items:
                        break
    return results
