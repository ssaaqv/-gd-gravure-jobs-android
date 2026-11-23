"""中国印刷人才网 (m.pjob.net) 广东凹版/软包装岗位采集模块"""
from __future__ import annotations

import time
from datetime import datetime
from typing import List
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record
from sources.common import fetch_html

PJOB_URLS = [
    "https://m.pjob.net/",
    "https://m.pjob.net/jobs/?keyword=%E5%87%B9%E5%8D%B0",
    "https://m.pjob.net/jobs/?keyword=%E5%87%B9%E7%89%88%E5%8D%B0%E5%88%B7",
    "https://m.pjob.net/jobs/?keyword=%E8%BD%AF%E5%8C%85%E8%A3%85",
]


def scrape(max_details: int = 20) -> List[JobPost]:
    results: List[JobPost] = []
    seen = set()

    for url in PJOB_URLS:
        try:
            html = fetch_html(url)
        except Exception as exc:
            print(f"[pjob][WARN] list failed {url}: {exc}")
            continue

        soup = BeautifulSoup(html, "lxml")
        for a in soup.find_all("a", href=True):
            href = a["href"]
            if ("/job/" in href or "/jobs/" in href or "/company/" in href) and href.endswith(".html"):
                full = urljoin(url, href)
                title = a.get_text(" ", strip=True)
                if full in seen or len(title) < 3:
                    continue
                seen.add(full)
                if len(seen) > max_details:
                    break

                time.sleep(1.5)
                try:
                    d_html = fetch_html(full)
                except Exception:
                    continue

                body = BeautifulSoup(d_html, "lxml").get_text(" ", strip=True)
                post = parse_job_record(
                    title=title,
                    body=body[:900],
                    location_hint="",
                    source_name="中国印刷人才网",
                    source_url=full,
                    published_at=datetime.now().strftime("%Y-%m-%d"),
                )
                if post:
                    results.append(post)

    return results
