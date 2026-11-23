"""新岗位 + AI 每日精选免费推送模块（支持企业微信 / 飞书 / Bark）"""
from __future__ import annotations

import os
from typing import Dict, List, Optional
from urllib.parse import quote

import requests


def notify_new_jobs(new_jobs: List[Dict], ai_digest: Optional[Dict] = None) -> None:
    if not new_jobs and not ai_digest:
        return

    parts: List[str] = []
    if ai_digest and ai_digest.get("summary"):
        parts.append(f"【粤印求职 · {ai_digest.get('mode', 'AI精选')}】\n{ai_digest['summary']}")

    if new_jobs:
        parts.append(f"\n本次新增 {len(new_jobs)} 条广东凹印岗位：")
        for j in new_jobs[:5]:
            parts.append(
                f"- [{j['city']}] {j['title']} | {j['salary_text']} | 电话:{j.get('phone') or '见详情'}"
            )

    content = "\n".join(parts)

    wecom_url = os.getenv("WECOM_WEBHOOK", "").strip()
    if wecom_url:
        try:
            requests.post(wecom_url, json={"msgtype": "text", "text": {"content": content}}, timeout=10)
        except Exception as exc:
            print(f"[notify][WARN] WeCom failed: {exc}")

    feishu_url = os.getenv("FEISHU_WEBHOOK", "").strip()
    if feishu_url:
        try:
            requests.post(feishu_url, json={"msg_type": "text", "content": {"text": content}}, timeout=10)
        except Exception as exc:
            print(f"[notify][WARN] Feishu failed: {exc}")

    bark_base = os.getenv("BARK_URL", "").strip().rstrip("/")
    if bark_base:
        try:
            title = quote(f"粤印今日精选（新增{len(new_jobs)}条）")
            body = quote(content[:240])
            requests.get(f"{bark_base}/{title}/{body}", timeout=10)
        except Exception as exc:
            print(f"[notify][WARN] Bark failed: {exc}")
