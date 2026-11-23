"""
广东塑料袋凹版印刷 · 每日低频抓取器
说明：
1. 默认面向软包装垂直静态页面低频礼貌抓取（每次请求间隔 2~3 秒，仅抓前 3 页）。
2. 自动与历史 jobs.json 合并去重，保留最近 90 天记录，并统计同一电话高频招人次数（避坑预警）。
3. 可按相同接口扩展更多广东地方人才网或企业官网。
"""
from __future__ import annotations

import argparse
import json
import re
import time
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Tuple
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from job_parser import JobPost, parse_job_record

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "zh-CN,zh;q=0.9",
}

# 垂直源配置：天下软包装（广东/凹印相关公开检索入口）
TXRBZ_SEARCH_URLS = [
    "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%B6%AB",  # 广东
    "http://www.txrbz.com/Search.asp?KeyWord=%E2%D6%B2%BA",  # 庵埠
    "http://www.txrbz.com/Search.asp?KeyWord=%B6%AB%DD%B8",  # 东莞
    "http://www.txrbz.com/Search.asp?KeyWord=%B7%F0%C9%BD",  # 佛山
    "http://www.txrbz.com/Search.asp?KeyWord=%B9%E3%D6%DD",  # 广州
]


def fetch_html(url: str, timeout: int = 15) -> str:
    resp = requests.get(url, headers=HEADERS, timeout=timeout)
    resp.raise_for_status()
    resp.encoding = resp.apparent_encoding or "utf-8"
    return resp.text


def scrape_txrbz(max_details_per_query: int = 15) -> List[JobPost]:
    results: List[JobPost] = []
    seen_urls = set()

    for list_url in TXRBZ_SEARCH_URLS:
        try:
            html = fetch_html(list_url)
        except Exception as exc:
            print(f"[WARN] Skip list {list_url}: {exc}")
            continue

        soup = BeautifulSoup(html, "lxml")
        detail_links: List[Tuple[str, str]] = []
        for a in soup.find_all("a", href=True):
            href = a["href"]
            if "/info/" in href and href.endswith(".html"):
                full_url = urljoin(list_url, href)
                title = a.get_text(" ", strip=True)
                if full_url not in seen_urls and len(title) >= 4:
                    seen_urls.add(full_url)
                    detail_links.append((full_url, title))

        for detail_url, link_title in detail_links[:max_details_per_query]:
            time.sleep(2.2)  # 礼貌限速，避免对目标站造成压力
            try:
                d_html = fetch_html(detail_url)
            except Exception as exc:
                print(f"[WARN] Skip detail {detail_url}: {exc}")
                continue

            d_soup = BeautifulSoup(d_html, "lxml")
            page_text = d_soup.get_text("\n", strip=True)

            # 提取发布时间
            m_date = re.search(r"发布时间[：:]\s*(\d{4}[/-]\d{1,2}[/-]\d{1,2})", page_text)
            pub_date = m_date.group(1).replace("/", "-") if m_date else datetime.now().strftime("%Y-%m-%d")

            # 提取详情正文区间
            m_body = re.search(r"详情[：:](.+?)(?:分享本信息|上一篇|相关留言|$)", page_text, re.S)
            body_text = m_body.group(1).strip() if m_body else page_text[:600]

            # 提取地区提示
            m_loc = re.search(r"(广东[^\s\n]{0,6})", page_text)
            loc_hint = m_loc.group(1) if m_loc else ""

            post = parse_job_record(
                title=link_title,
                body=body_text,
                location_hint=loc_hint,
                source_name="天下软包装",
                source_url=detail_url,
                published_at=pub_date,
            )
            if post:
                results.append(post)

    return results


def merge_and_save(new_posts: List[JobPost], output_path: Path, keep_days: int = 90) -> Dict:
    existing_items: List[Dict] = []
    if output_path.exists():
        try:
            old_data = json.loads(output_path.read_text(encoding="utf-8"))
            existing_items = old_data.get("jobs", [])
        except Exception:
            existing_items = []

    merged: Dict[str, Dict] = {}
    for item in existing_items:
        merged[item["id"]] = item
    for post in new_posts:
        merged[post.id] = post.to_dict()

    cutoff = (datetime.now() - timedelta(days=keep_days)).strftime("%Y-%m-%d")
    active_jobs = [
        j for j in merged.values()
        if j.get("published_at", "9999-99-99") >= cutoff
    ]

    # 统计同一电话在近 90 天内重复发帖次数（避坑预警指标）
    phone_counter = Counter(j["phone"] for j in active_jobs if j.get("phone"))
    for j in active_jobs:
        phone = j.get("phone", "")
        j["repeat_count"] = phone_counter[phone] if phone else 1

    # 按发布日期倒序、相关度倒序排列
    active_jobs.sort(key=lambda x: (x.get("published_at", ""), x.get("relevance_score", 0)), reverse=True)

    payload = {
        "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "region": "广东",
        "industry": "塑料袋凹版印刷",
        "total_count": len(active_jobs),
        "jobs": active_jobs,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload


def main() -> None:
    ap = argparse.ArgumentParser(description="广东塑料袋凹版印刷每日抓取器")
    ap.add_argument("--output", default="data/jobs.json", help="输出 JSON 路径")
    ap.add_argument("--max-pages", type=int, default=3, help="每个查询最多抓取详情条数倍率")
    args = ap.parse_args()

    posts = scrape_txrbz(max_details_per_query=args.max_pages * 5)
    payload = merge_and_save(posts, Path(args.output))
    print(f"[OK] Saved {payload['total_count']} Guangdong gravure jobs to {args.output}")


if __name__ == "__main__":
    main()
