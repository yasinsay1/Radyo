#!/usr/bin/env python3
"""İki açık Türkçe radyo listesini indirir, temizler, birleştirir ve radyolar.m3u yazar."""
import re
import sys
import urllib.request

SOURCES = [
    "https://raw.githubusercontent.com/AlonDrilich/radio-playlists/main/countries/tr.m3u",
    "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/online_radio_box/turkish.m3u",
]

# Radyo olmayan ya da çalmayacağı belli adresler
BAD_URL = re.compile(
    r"(1080p|720p|tv24|chat_sound|cc_streaminfo|\.pls(\?|$)|listen\.pls|\*|\s)",
    re.IGNORECASE,
)
ATTR = re.compile(r'([\w-]+)\s*=\s*"([^"]*)"')


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "radyo-merge/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", errors="replace")


def first_comma_outside_quotes(s):
    inq = False
    for i, ch in enumerate(s):
        if ch == '"':
            inq = not inq
        elif ch == "," and not inq:
            return i
    return -1


def parse(text):
    out = []
    name = logo = None
    for raw in text.lstrip("﻿").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.upper().startswith("#EXTINF"):
            body = line.split(":", 1)[1] if ":" in line else ""
            c = first_comma_outside_quotes(body)
            attrs = {k.lower(): v for k, v in ATTR.findall(body[:c] if c >= 0 else body)}
            title = body[c + 1:].strip() if c >= 0 else ""
            name = title or attrs.get("tvg-name")
            logo = attrs.get("tvg-logo")
        elif line.startswith("#"):
            continue
        elif re.match(r"https?://", line, re.IGNORECASE):
            if name and not BAD_URL.search(line):
                out.append((name.replace('"', "'"), line, logo))
            name = logo = None
    return out


def norm_name(n):
    return re.sub(r"[^0-9a-zçğıöşü]", "", n.casefold())


def norm_url(u):
    return re.sub(r"^https?://", "", u.strip().lower()).rstrip("/")


def main():
    seen_names, seen_urls, merged = set(), set(), []
    for src in SOURCES:
        entries = parse(fetch(src))
        print(f"{len(entries)} kayıt: {src}")
        if len(entries) < 100:
            sys.exit(f"Kaynak beklenenden küçük, işlem durduruldu: {src}")
        for name, url, logo in entries:
            nn, nu = norm_name(name), norm_url(url)
            if not nn or nn in seen_names or nu in seen_urls:
                continue
            seen_names.add(nn)
            seen_urls.add(nu)
            merged.append((name, url, logo))

    merged.sort(key=lambda e: e[0].casefold())
    lines = ["#EXTM3U"]
    for name, url, logo in merged:
        logo_attr = f' tvg-logo="{logo}"' if logo else ""
        lines.append(f"#EXTINF:-1{logo_attr},{name}")
        lines.append(url)
    with open("radyolar.m3u", "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Toplam {len(merged)} radyo yazıldı.")


if __name__ == "__main__":
    main()
