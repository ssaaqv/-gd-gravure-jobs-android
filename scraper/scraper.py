"""
广东塑料袋凹版印刷 · 24小时多源总控抓取器 (v3.0)
每个数据源独立异常隔离，互不影响；自动合并去重、统计高频招人次数，并支持新岗推送。
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List

sys.path.insert(0, str(Path(__file__).resolve().parent))

from job_parser import JobPost
from ai_digest import generate_daily_digest
from notifier import notify_new_jobs
from sources import email_feed, gd_hrss, gd_local, job5156, liepin_web, lzzxcn, pjob, txrbz


def run_all_sources(max_pages: int = 3) -> tuple[List[JobPost], Dict[str, int]]:
    all_posts: List[JobPost] = []
    source_stats: Dict[str, int] = {}
    base_dir = Path(__file__).resolve().parent

    runners = [
        ("天下软包装", lambda: txrbz.scrape(max_per_query=max_pages * 4)),
        ("中国印刷人才网", lambda: pjob.scrape(max_details=max_pages * 8)),
        ("智通直聘", lambda: job5156.scrape(max_items=max_pages * 10)),
        ("联展资讯网", lambda: lzzxcn.scrape(max_items=max_pages * 8)),
        ("猎聘公开页", lambda: liepin_web.scrape(max_items=max_pages * 6)),
        ("广东人社官方", lambda: gd_hrss.scrape(max_items=max_pages * 5)),
        ("广东地方站", lambda: gd_local.scrape(base_dir / "custom_urls.json")),
        ("邮箱订阅", lambda: email_feed.scrape(max_emails=20)),
    ]

    for name, fn in runners:
        try:
            posts = fn()
            source_stats[name] = len(posts)
            all_posts.extend(posts)
            print(f"[SOURCE OK] {name}: {len(posts)} items")
        except Exception as exc:
            source_stats[name] = -1
            print(f"[SOURCE ERR] {name}: {exc}")

    return all_posts, source_stats


def merge_and_save(
    new_posts: List[JobPost],
    source_stats: Dict[str, int],
    output_path: Path,
    keep_days: int = 90,
) -> tuple[Dict, List[Dict]]:
    existing_items: List[Dict] = []
    if output_path.exists():
        try:
            old_data = json.loads(output_path.read_text(encoding="utf-8"))
            existing_items = old_data.get("jobs", [])
        except Exception:
            existing_items = []

    old_ids = {item["id"] for item in existing_items if "id" in item}
    merged: Dict[str, Dict] = {item["id"]: item for item in existing_items if "id" in item}

    newly_added: List[Dict] = []
    for post in new_posts:
        d = post.to_dict()
        if post.id not in old_ids:
            newly_added.append(d)
        merged[post.id] = d

    cutoff = (datetime.now() - timedelta(days=keep_days)).strftime("%Y-%m-%d")
    active_jobs = [
        j for j in merged.values()
        if j.get("published_at", "9999-99-99") >= cutoff
    ]

    phone_counter = Counter(j["phone"] for j in active_jobs if j.get("phone"))
    for j in active_jobs:
        phone = j.get("phone", "")
        j["repeat_count"] = phone_counter[phone] if phone else 1

    active_jobs.sort(key=lambda x: (x.get("published_at", ""), x.get("relevance_score", 0)), reverse=True)

    ai_digest = generate_daily_digest(active_jobs)

    payload = {
        "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "ai_digest": ai_digest,
        "region": "广东",
        "industry": "塑料袋凹版印刷",
        "total_count": len(active_jobs),
        "source_stats": source_stats,
        "jobs": active_jobs,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload, newly_added


def main() -> None:
    ap = argparse.ArgumentParser(description="广东塑料袋凹版印刷24小时多源抓取器")
    ap.add_argument("--output", default="data/jobs.json", help="输出 JSON 路径")
    ap.add_argument("--max-pages", type=int, default=3, help="单源抓取倍率")
    args = ap.parse_args()

    posts, stats = run_all_sources(max_pages=args.max_pages)
    payload, newly_added = merge_and_save(posts, stats, Path(args.output))
    notify_new_jobs(newly_added, payload.get('ai_digest'))
    print(f"[DONE] Total={payload['total_count']}, New={len(newly_added)}, Stats={stats}")


if __name__ == "__main__":
    main()
