"""广东地方人才网 & 用户自定义网站扩展采集模块"""
from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import List

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record
from sources.common import fetch_html


def scrape(config_path: Path) -> List[JobPost]:
    results: List[JobPost] = []
    if not config_path.exists():
        return results

    try:
        cfg = json.loads(config_path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"[gd_local][WARN] bad config: {exc}")
        return results

    for site in cfg.get("sites", []):
        if not site.get("enabled", True):
            continue
        url = site.get("list_url", "")
        name = site.get("name", "广东地方站")
        loc_hint = site.get("location_hint", "广东")
        if not url:
            continue
        try:
            html = fetch_html(url)
            text = BeautifulSoup(html, "lxml").get_text(" ", strip=True)
            post = parse_job_record(
                title=f"{name} 凹印/印刷招聘",
                body=text[:900],
                location_hint=loc_hint,
                source_name=name,
                source_url=url,
                published_at=datetime.now().strftime("%Y-%m-%d"),
            )
            if post:
                results.append(post)
        except Exception as exc:
            print(f"[gd_local][WARN] {name} failed: {exc}")

    return results
