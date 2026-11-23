from __future__ import annotations

import re
import time
from datetime import datetime
from typing import List, Tuple
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record
from sources.common import fetch_html

TXRBZ_SEARCH_URLS = [
    "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%B6%AB",  # 广东
    "http://www.txrbz.com/Search.asp?KeyWord=%E2%D6%B2%BA",  # 庵埠
    "http://www.txrbz.com/Search.asp?KeyWord=%B3%B1%D6%DD",  # 潮州
    "http://www.txrbz.com/Search.asp?KeyWord=%B3%B1%B0%B2",  # 潮安
    "http://www.txrbz.com/Search.asp?KeyWord=%C9%C7%CD%B7",  # 汕头
    "http://www.txrbz.com/Search.asp?KeyWord=%B3%B1%C4%CF",  # 潮南
    "http://www.txrbz.com/Search.asp?KeyWord=%B6%AB%DD%B8",  # 东莞
    "http://www.txrbz.com/Search.asp?KeyWord=%B7%F0%C9%BD",  # 佛山
    "http://www.txrbz.com/Search.asp?KeyWord=%CB%B3%B5%C2",  # 顺德
    "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%D6%DD",  # 广州
    "http://www.txrbz.com/Search.asp?KeyWord=%C9%EE%DB%DA",  # 深圳
    "http://www.txrbz.com/Search.asp?KeyWord=%D6%D0%C9%BD",  # 中山
    "http://www.txrbz.com/Search.asp?KeyWord=%BB%DD%D6%DD",  # 惠州
    "http://www.txrbz.com/Search.asp?KeyWord=%BD%AD%C3%C5",  # 江门
    "http://www.txrbz.com/Search.asp?KeyWord=%BD%D2%D1%F4",  # 揭阳
]


def scrape(max_per_query: int = 12) -> List[JobPost]:
    results: List[JobPost] = []
    seen = set()

    for list_url in TXRBZ_SEARCH_URLS:
        try:
            html = fetch_html(list_url)
        except Exception as exc:
            print(f"[txrbz][WARN] list failed {list_url}: {exc}")
            continue

        soup = BeautifulSoup(html, "lxml")
        links: List[Tuple[str, str]] = []
        for a in soup.find_all("a", href=True):
            href = a["href"]
            if "/info/" in href and href.endswith(".html"):
                full = urljoin(list_url, href)
                title = a.get_text(" ", strip=True)
                if full not in seen and len(title) >= 4:
                    seen.add(full)
                    links.append((full, title))

        for detail_url, title in links[:max_per_query]:
            time.sleep(1.8)
            try:
                d_html = fetch_html(detail_url)
            except Exception:
                continue

            text = BeautifulSoup(d_html, "lxml").get_text("\n", strip=True)
            m_date = re.search(r"发布时间[：:]\s*(\d{4}[/-]\d{1,2}[/-]\d{1,2})", text)
            pub = m_date.group(1).replace("/", "-") if m_date else datetime.now().strftime("%Y-%m-%d")
            m_body = re.search(r"详情[：:](.+?)(?:分享本信息|上一篇|相关留言|$)", text, re.S)
            body = m_body.group(1).strip() if m_body else text[:600]
            m_loc = re.search(r"(广东[^\s\n]{0,6})", text)
            loc = m_loc.group(1) if m_loc else ""

            post = parse_job_record(
                title=title,
                body=body,
                location_hint=loc,
                source_name="天下软包装",
                source_url=detail_url,
                published_at=pub,
            )
            if post:
                results.append(post)

    return results
