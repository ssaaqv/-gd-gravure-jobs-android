"""
全免费极简 AI 每日精选引擎（无需花钱买 API）
优先级：
1. 若用户手动配置了 AI_API_KEY，则使用用户自定义 OpenAI 兼容接口；
2. 否则自动使用 GitHub Actions 内置的免费 GITHUB_TOKEN 调用 GitHub Models (gpt-4o-mini)，完全免费；
3. 若限流或离线，无缝降级为本地规则评分引擎，确保每天都有“今日有价值岗位”卡片。
"""
from __future__ import annotations

import os
from datetime import datetime
from typing import Dict, List

import requests


def _score_job_locally(job: Dict) -> int:
    score = int(job.get("relevance_score", 0))
    sal_max = int(job.get("salary_max", 0))
    if sal_max >= 15000:
        score += 35
    elif sal_max >= 12000:
        score += 25
    elif sal_max >= 8000:
        score += 15

    if job.get("shift") == "长白班":
        score += 12
    benefits = job.get("benefit_tags", [])
    if "包吃住" in benefits:
        score += 10
    if "交社保" in benefits:
        score += 10
    if job.get("phone"):
        score += 8
    if int(job.get("repeat_count", 1)) >= 3:
        score -= 20
    return score


def _local_fallback_digest(jobs: List[Dict]) -> Dict:
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M")
    if not jobs:
        return {
            "generated_at": now_str,
            "mode": "本地免费精选",
            "summary": "暂未抓到新岗位，请点击右上角刷新或用右下角“粘贴导入”存入大平台/微信群岗位。",
        }

    ranked = sorted(jobs, key=_score_job_locally, reverse=True)
    top = ranked[:3]
    lines = ["【今日最值得联系（免费智能评分）】"]
    for idx, j in enumerate(top, 1):
        tags = " / ".join((j.get("machine_tags", []) + j.get("benefit_tags", []))[:3])
        extra = f"（{tags}）" if tags else ""
        tel = f" 电话:{j['phone']}" if j.get("phone") else ""
        lines.append(f"{idx}. [{j['city']}] {j['title']} · {j['salary_text']}{extra}{tel}")

    warns = [j for j in jobs if int(j.get("repeat_count", 1)) >= 3]
    if warns:
        lines.append(f"避坑提醒：有 {len(warns)} 条岗位近90天反复招人，打电话时注意问清车间环境与发薪日。")

    return {
        "generated_at": now_str,
        "mode": "本地免费精选",
        "summary": "\n".join(lines),
    }


def generate_daily_digest(jobs: List[Dict]) -> Dict:
    if not jobs:
        return _local_fallback_digest(jobs)

    custom_key = os.getenv("AI_API_KEY", "").strip()
    gh_token = os.getenv("GITHUB_TOKEN", "").strip()

    if custom_key:
        api_key = custom_key
        api_base = os.getenv("AI_API_BASE", "https://api.deepseek.com/v1").strip().rstrip("/")
        model = os.getenv("AI_MODEL", "deepseek-chat").strip()
        mode_label = f"AI精选 ({model})"
    elif gh_token:
        # GitHub Actions 免费内置模型，无需申请额外 API Key，零费用
        api_key = gh_token
        api_base = "https://models.inference.ai.azure.com"
        model = "gpt-4o-mini"
        mode_label = "免费AI精选 (GitHub)"
    else:
        return _local_fallback_digest(jobs)

    candidates = sorted(jobs, key=_score_job_locally, reverse=True)[:15]
    brief_lines = []
    for i, j in enumerate(candidates, 1):
        brief_lines.append(
            f"{i}. [{j['city']}] {j['title']} | 岗位:{j['role']} | 薪资:{j['salary_text']} | "
            f"班次:{j['shift']} {j['work_days']} | 机型:{','.join(j.get('machine_tags', []))} | "
            f"福利:{','.join(j.get('benefit_tags', []))} | 电话:{j.get('phone') or '无'} | 近90天发帖:{j.get('repeat_count', 1)}次"
        )

    prompt = (
        "你是广东塑料袋凹版印刷行业的老师傅。请根据下面最新抓到的广东岗位，"
        "用接地气的大白话（180字以内）输出今日求职建议：\n"
        "1. 挑出最值得立刻打电话的 2~3 个岗位（写清城市、薪资/机型亮点、为什么值）。\n"
        "2. 提醒要避坑的地方（比如高频反复招人、不写薪资、无社保等）。\n"
        "直接说重点：\n\n" + "\n".join(brief_lines)
    )

    try:
        resp = requests.post(
            f"{api_base}/chat/completions",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.3,
                "max_tokens": 300,
            },
            timeout=25,
        )
        resp.raise_for_status()
        summary = resp.json()["choices"][0]["message"]["content"].strip()
        return {
            "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "mode": mode_label,
            "summary": summary,
        }
    except Exception as exc:
        print(f"[ai_digest][WARN] Free AI failed, fallback to local engine: {exc}")
        return _local_fallback_digest(jobs)
