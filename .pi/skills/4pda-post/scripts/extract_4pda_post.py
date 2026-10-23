#!/usr/bin/env python3
"""Извлечение постов 4PDA в чистый текст.

Использование:
    python extract_4pda_post.py 143495976
    python extract_4pda_post.py "https://4pda.to/forum/index.php?showtopic=1117490&view=findpost&p=143495976"
    python extract_4pda_post.py 143495976 --all        # все посты на этой странице
    python extract_4pda_post.py 143495976 --links      # + список ссылок
    python extract_4pda_post.py 143495976 --json       # машиночитаемо

Требует только stdlib. Возвращает 1, если пост не найден на странице.
"""
from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.error
import urllib.request

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
)
BASE = "https://4pda.to/forum/index.php"


def fetch(url: str, timeout: int = 60, retries: int = 2) -> str:
    """Скачивает страницу и декодирует её в правильной кодировке (4PDA = cp1251)."""
    last: Exception | None = None
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": UA, "Accept-Language": "ru-RU,ru;q=0.9"}
            )
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
            return decode(raw)
        except (urllib.error.URLError, TimeoutError) as exc:  # сеть/таймаут
            last = exc
            if attempt < retries:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"не удалось скачать {url}: {last}")


def decode(raw: bytes) -> str:
    head = raw[:4096].decode("ascii", errors="ignore").lower()
    m = re.search(r'charset=["\']?([\w-]+)', head)
    encodings = []
    if m:
        encodings.append(m.group(1))
    encodings += ["cp1251", "utf-8"]
    for enc in encodings:
        try:
            text = raw.decode(enc)
        except (UnicodeDecodeError, LookupError):
            continue
        if text.count("\ufffd") < 5:
            return text
    return raw.decode("cp1251", errors="replace")


def clean(fragment: str) -> str:
    """HTML-фрагмент -> читаемый текст, с маркерами спойлеров и цитат."""
    t = fragment
    t = re.sub(r'<div class="spoil[^"]*"[^>]*>', "\n[СПОЙЛЕР]\n", t)
    t = re.sub(r'<div class="quote[^"]*"[^>]*>', "\n[ЦИТАТА]\n", t)
    t = re.sub(r"<script.*?</script>|<style.*?</style>", "", t, flags=re.S | re.I)
    t = re.sub(r"<br\s*/?>", "\n", t, flags=re.I)
    t = re.sub(r"</(p|div|li|tr|h[1-6]|table)>", "\n", t, flags=re.I)
    t = re.sub(r"<img[^>]*?alt=\"([^\"]*)\"[^>]*>", r" \1 ", t, flags=re.I)
    t = re.sub(r"<[^>]+>", "", t)
    t = html.unescape(t).replace("\xa0", " ")
    t = re.sub(r"[ \t]+", " ", t)
    t = re.sub(r" *\n *", "\n", t)
    t = re.sub(r"\n{3,}", "\n\n", t)
    return t.strip()


def strip_emoji(text: str) -> str:
    """Убирает :superman: и подобные смайлы 4PDA."""
    return re.sub(r"\s:[a-z0-9_]+:\s", " ", text)


def topic_title(page: str) -> str:
    m = re.search(r"<title>(.*?)</title>", page, re.S | re.I)
    return html.unescape(m.group(1)).strip() if m else ""


def split_posts(page: str) -> dict[str, str]:
    """pid -> HTML одного поста (от data-post до следующего data-post)."""
    marks = [(m.group(1), m.start()) for m in re.finditer(r'<div data-post="(\d+)"', page)]
    out: dict[str, str] = {}
    for idx, (pid, start) in enumerate(marks):
        end = marks[idx + 1][1] if idx + 1 < len(marks) else len(page)
        out[pid] = page[start:end]
    return out


def parse_post(pid: str, chunk: str) -> dict:
    date = re.search(r'class="post_date">(.*?)&nbsp;', chunk, re.S)
    num = re.search(r"link_to_post\(\d+\); return false;\">#(\d+)<", chunk)
    author = re.search(r'class="post_nick".*?<a[^>]*>(.*?)</a>', chunk, re.S)
    author_name = re.sub(r"[\u1d65\u2022\u25cf]", "", clean(author.group(1))).strip() if author else None
    body_start = chunk.find(f'id="post-main-{pid}"')
    body = chunk[body_start:] if body_start >= 0 else chunk
    body = re.split(r'class="post_sig|id="post-edit|Сообщение отредактировал', body)[0]
    if body_start >= 0:
        body = body.split(">", 1)[1] if ">" in body else body
    edited = re.search(r"Сообщение отредактировал\s*(.*?)</", chunk, re.S)
    text = strip_emoji(clean(body))
    links = [html.unescape(u) for u in re.findall(r'href="(https?://[^"]+)"', chunk)]
    links = [u for u in dict.fromkeys(links) if "/showuser=" not in u and "act=rep" not in u]
    return {
        "pid": pid,
        "num": num.group(1) if num else None,
        "author": author_name,
        "date": clean(date.group(1)) if date else None,
        "edited": clean(edited.group(1)) if edited else None,
        "text": text,
        "links": links,
    }


def parse_target(target: str) -> str:
    """pid или URL -> URL, который редиректит на нужный пост."""
    m = re.search(r"[?&]p=(\d+)", target)
    if m:
        return f"{BASE}?act=findpost&pid={m.group(1)}"
    m = re.search(r"\b(\d{6,})\b", target)
    if m:
        return f"{BASE}?act=findpost&pid={m.group(1)}"
    raise SystemExit("не найден pid: передайте число (143495976) или ссылку с p=...")


def main() -> int:
    # Консоль Windows по умолчанию cp1251: без этого падает на ✓/эмодзи в тексте поста.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    ap = argparse.ArgumentParser()
    ap.add_argument("target", help="pid поста или ссылка на него")
    ap.add_argument("--all", action="store_true", help="вывести все посты страницы")
    ap.add_argument("--links", action="store_true", help="показать ссылки поста")
    ap.add_argument("--json", action="store_true", help="вывод в JSON")
    args = ap.parse_args()

    url = parse_target(args.target)
    page = fetch(url)
    posts = split_posts(page)
    wanted = re.search(r"pid=(\d+)", url).group(1)
    if wanted not in posts:
        print(f"пост {wanted} не найден на странице (получено {len(posts)} постов)", file=sys.stderr)
        return 1

    selected = list(posts) if args.all else [wanted]
    result = [parse_post(pid, posts[pid]) for pid in selected]
    if args.json:
        print(json.dumps({"topic": topic_title(page), "posts": result}, ensure_ascii=False, indent=2))
        return 0

    print(f"# {topic_title(page)}\n")
    for p in result:
        print(f"## Пост #{p['num']} (pid={p['pid']}) — {p['author']}, {p['date']}")
        if p["edited"]:
            print(f"_edited: {p['edited']}_")
        print()
        print(p["text"])
        if args.links and p["links"]:
            print("\nСсылки:")
            for u in p["links"]:
                print(" -", u)
        print("\n" + "-" * 60 + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
