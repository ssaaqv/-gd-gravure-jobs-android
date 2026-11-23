"""
可选零反爬大招：IMAP 职位订阅邮件自动同步模块
在 GitHub Secrets 中配置 IMAP_HOST / IMAP_USER / IMAP_PASS 后，
自动读取你邮箱中来自各大招聘平台的每日职位订阅邮件，提取广东凹印岗位。
"""
from __future__ import annotations

import email
import imaplib
import os
from datetime import datetime
from email.header import decode_header
from typing import List

from bs4 import BeautifulSoup

from job_parser import JobPost, parse_job_record


def _decode_str(raw: str) -> str:
    parts = decode_header(raw or "")
    out = []
    for data, enc in parts:
        if isinstance(data, bytes):
            out.append(data.decode(enc or "utf-8", errors="ignore"))
        else:
            out.append(str(data))
    return "".join(out)


def scrape(max_emails: int = 20) -> List[JobPost]:
    host = os.getenv("IMAP_HOST", "").strip()
    user = os.getenv("IMAP_USER", "").strip()
    password = os.getenv("IMAP_PASS", "").strip()
    if not (host and user and password):
        return []

    results: List[JobPost] = []
    try:
        mail = imaplib.IMAP4_SSL(host, timeout=15)
        mail.login(user, password)
        mail.select("INBOX")
        _, data = mail.search(None, "ALL")
        ids = data[0].split()[-max_emails:]

        for msg_id in reversed(ids):
            _, msg_data = mail.fetch(msg_id, "(RFC822)")
            if not msg_data or not isinstance(msg_data[0], tuple):
                continue
            msg = email.message_from_bytes(msg_data[0][1])
            subject = _decode_str(msg.get("Subject", ""))

            body_parts = []
            if msg.is_multipart():
                for part in msg.walk():
                    ctype = part.get_content_type()
                    if ctype in ("text/plain", "text/html"):
                        payload = part.get_payload(decode=True)
                        if payload:
                            txt = payload.decode(part.get_content_charset() or "utf-8", errors="ignore")
                            if ctype == "text/html":
                                txt = BeautifulSoup(txt, "lxml").get_text(" ", strip=True)
                            body_parts.append(txt)
            else:
                payload = msg.get_payload(decode=True)
                if payload:
                    body_parts.append(payload.decode(msg.get_content_charset() or "utf-8", errors="ignore"))

            full_body = "\n".join(body_parts)[:1200]
            post = parse_job_record(
                title=subject or "邮件订阅凹印岗位",
                body=full_body,
                location_hint="",
                source_name="邮箱订阅",
                source_url="",
                published_at=datetime.now().strftime("%Y-%m-%d"),
            )
            if post:
                results.append(post)
        mail.logout()
    except Exception as exc:
        print(f"[email_feed][WARN] IMAP error: {exc}")

    return results
