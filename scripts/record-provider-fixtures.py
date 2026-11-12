#!/usr/bin/env python3
"""Records provider answers as test fixtures (`rec-` files), trimmed and cleaned.

Stdlib only. This repository is public, so every recording is trimmed and cleaned, and the
script writes into the fixture folders only after scripts/check-fixture-privacy.sh passes on the
cleaned files. Read the diff by hand before a commit all the same.

Two sources, and no other:
  xtream  the local jellyxtream bridge (tools/jellyfin-xtream/xtream_bridge.py), on 127.0.0.1 or
          localhost only: login, the category and list actions, get_vod_info and get_series_info
          for the first film and show kept, and get_live_streams, get_short_epg,
          get_simple_data_table and /xmltv.php when the library has live channels.
  m3u     the public iptv-org playlist (https://iptv-org.github.io/iptv/index.m3u, released under
          the Unlicense: check that this is still true before a commit).

The bridge login comes from the environment, never from an argument or a file:
  FIXTURE_XTREAM_USER, FIXTURE_XTREAM_PASS   the bridge login (a Jellyfin login)
  FIXTURE_XTREAM_URL                         the bridge (default: http://127.0.0.1:8080)

Trimming: 3 to 5 items in a list; items with a set of keys not seen yet go first, so each shape
the source sends is kept. Cleaning: the login, the host and the port become user, pass and
provider.invalid; server and account times become fixed values; every URL host becomes
img.invalid (pictures) or stream.invalid (the path stays: the parsers read extensions); the
api_key, token and X-Emby-Token query parameters are removed; Jellyfin ids (32 hex characters)
become fixed ids; titles, plots and names of people become placeholders that keep the shape
("EN - Film 1 (2004)"), as the provider data tests plan decided (PD-Q1).

Usage:
  scripts/record-provider-fixtures.py [--source xtream|m3u|all] [--out DIR]
    --source  what to record (default: all)
    --out     write <DIR>/<kind>/ instead of the fixture folders (for a dry run)
"""

import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATE = ROOT / "scripts" / "check-fixture-privacy.sh"
DESTINATIONS = {
    "xtream": ROOT / "core/player/src/test/resources/fixtures/xtream",
    "m3u": ROOT / "core/network/src/test/resources/fixtures/m3u",
    "xmltv": ROOT / "core/network/src/test/resources/fixtures/xmltv",
}
M3U_URL = "https://iptv-org.github.io/iptv/index.m3u"
LOCAL_HOSTS = {"127.0.0.1", "localhost"}
USER_AGENT = "fixture-recorder/1.0"

LIST_ITEMS = 4
M3U_ITEMS = 5
EPISODES_PER_SEASON = 3
SEASONS = 2
GUIDE_ITEMS = 4

# Fixed values: 2026-01-01 00:00:00 UTC, an expiry a year later, a creation a year before.
FIXED_NOW = 1767225600
FIXED = {
    ("server_info", "url"): "provider.invalid",
    ("server_info", "port"): "80",
    ("server_info", "https_port"): "",
    ("server_info", "timestamp_now"): FIXED_NOW,
    ("server_info", "time_now"): "2026-01-01 00:00:00",
    ("user_info", "username"): "user",
    ("user_info", "password"): "pass",
    ("user_info", "exp_date"): str(FIXED_NOW + 365 * 86400),
    ("user_info", "created_at"): str(FIXED_NOW - 365 * 86400),
}
PICTURE_KEYS = {"stream_icon", "cover", "cover_big", "movie_image", "backdrop_path", "tvg-logo", "icon"}
TITLE_KEYS = {"name", "title", "category_name"}
PLOT_KEYS = {"plot", "description", "overview"}
PEOPLE_KEYS = {"cast", "director"}
SECRET_PARAMS = {"api_key", "apikey", "token", "x-emby-token"}

HEX_ID = re.compile(r"\b[0-9a-fA-F]{32}\b|\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")
LANGUAGE_TAG = re.compile(r"^([A-Z0-9]{2,4}(?:-[A-Z0-9]{2,4})?\s*(?:-|:|\|)\s+)")
YEAR = re.compile(r"\((\d{4})\)")
EPISODE_CODE = re.compile(r"\bS\d{1,2}\s*EP?\d{1,3}\b", re.IGNORECASE)
IPV4 = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
PLOT_TEXT = "This is placeholder text for a plot. "


class Cleaner:
    """Replaces what could name a person, a library or a provider. The same value always gets
    the same placeholder, so a film's name in the list and in its details still agree."""

    def __init__(self, secrets):
        # Longest first, so a password that holds the username is replaced whole.
        self.secrets = sorted(((s, r) for s, r in secrets if s), key=lambda p: -len(p[0]))
        self.ids = {}
        self.titles = {}
        self.people = {}
        self.tmdb = {}

    def fixed_id(self, match):
        key = match.group(0).lower().replace("-", "")
        return self.ids.setdefault(key, f"{len(self.ids) + 1:032x}")

    def text(self, value):
        for secret, placeholder in self.secrets:
            for form in {secret, urllib.parse.quote(secret, safe=""), urllib.parse.quote_plus(secret)}:
                value = value.replace(form, placeholder)
        return HEX_ID.sub(self.fixed_id, value)

    def url(self, value, picture):
        parts = urllib.parse.urlsplit(value)
        query = [
            (k, {"username": "user", "password": "pass"}.get(k.lower(), v))
            for k, v in urllib.parse.parse_qsl(parts.query, keep_blank_values=True)
            if k.lower() not in SECRET_PARAMS
        ]
        host = "img.invalid" if picture else "stream.invalid"
        path = self.text(parts.path)
        return urllib.parse.urlunsplit((parts.scheme, host, path, urllib.parse.urlencode(query, safe="/:,"), ""))

    def title(self, value, kind):
        if not value.strip():
            return value
        if kind == "Season" and re.fullmatch(r"Season \d+", value):
            return value
        if value not in self.titles:
            tag = LANGUAGE_TAG.match(value)
            year = YEAR.search(value)
            code = EPISODE_CODE.search(value)
            count = sum(1 for k in self.titles.values() if k[1] == kind) + 1
            parts = [f"{tag.group(1) if tag else ''}{kind} {count}"]
            parts += [code.group(0)] if code else []
            parts += [f"({year.group(1)})"] if year else []
            self.titles[value] = (" ".join(parts), kind)
        return self.titles[value][0]

    def plot(self, value):
        size = len(value)
        return (PLOT_TEXT * (size // len(PLOT_TEXT) + 1))[:size].rstrip() if size else value

    def names(self, value):
        names = [n for n in value.split(",") if n.strip()]
        for n in names:
            self.people.setdefault(n.strip(), f"Person {len(self.people) + 1}")
        return ", ".join(self.people[n.strip()] for n in names)

    def tmdb_id(self, value):
        key = str(value)
        if key in ("", "0", "None"):
            return value
        fixed = self.tmdb.setdefault(key, 10000 + len(self.tmdb) + 1)
        return fixed if isinstance(value, int) else str(fixed)


def title_kind(path):
    """The placeholder word for a title at this place in the answer."""
    joined = "/".join(path)
    if "category" in joined or path[-1] == "category_name":
        return "Category"
    if "epg_listings" in joined:
        return "Programme"
    if "seasons" in joined:
        return "Season"
    if "episodes" in joined:
        return "Episode"
    return {"live": "Channel", "series": "Show"}.get(path[0], "Film")


def clean_json(value, cleaner, path):
    """A cleaned copy of [value]; [path] is the keys above it, the first one the kind of answer."""
    key = path[-1] if path else ""
    parent = path[-2] if len(path) > 1 else ""
    if isinstance(value, dict):
        return {k: clean_json(v, cleaner, path + [k]) for k, v in value.items()}
    if isinstance(value, list):
        return [clean_json(v, cleaner, path + [key]) for v in value]
    if (parent, key) in FIXED:
        return FIXED[(parent, key)]
    if key in ("tmdb", "tmdb_id"):
        return cleaner.tmdb_id(value)
    if not isinstance(value, str):
        return value
    if key == "youtube_trailer" and value:
        return "trailer"
    if "epg_listings" in path and key in ("title", "description"):
        decoded = base64.b64decode(value).decode("utf-8", "replace") if value else ""
        plain = cleaner.title(decoded, "Programme") if key == "title" else cleaner.plot(decoded)
        return base64.b64encode(plain.encode()).decode()
    if value.startswith(("http://", "https://")):
        return cleaner.url(value, key in PICTURE_KEYS)
    if key in TITLE_KEYS:
        return cleaner.title(cleaner.text(value), title_kind(path))
    if key in PLOT_KEYS:
        return cleaner.plot(value)
    if key in PEOPLE_KEYS:
        return cleaner.names(value)
    return cleaner.text(value)


def trim(items, count=LIST_ITEMS):
    """Up to [count] items: first one of each set of keys not seen yet, then the rest in order."""
    shapes, first, rest = set(), [], []
    for item in items:
        shape = frozenset(item) if isinstance(item, dict) else type(item).__name__
        (first if shape not in shapes else rest).append(item)
        shapes.add(shape)
    return (first + rest)[:count]


def trim_series_info(info):
    episodes = info.get("episodes")
    if isinstance(episodes, dict):
        kept = dict(list(episodes.items())[:SEASONS])
        info["episodes"] = {season: trim(eps, EPISODES_PER_SEASON) for season, eps in kept.items()}
        if isinstance(info.get("seasons"), list):
            info["seasons"] = [s for s in info["seasons"] if str(s.get("season_number")) in kept][:SEASONS]
    return info


def trim_guide(answer):
    if isinstance(answer, dict) and isinstance(answer.get("epg_listings"), list):
        answer["epg_listings"] = answer["epg_listings"][:GUIDE_ITEMS]
    return answer


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read().decode("utf-8")


def record_xtream(scratch):
    base = os.environ.get("FIXTURE_XTREAM_URL", "http://127.0.0.1:8080").rstrip("/")
    user = os.environ.get("FIXTURE_XTREAM_USER", "")
    password = os.environ.get("FIXTURE_XTREAM_PASS", "")
    parts = urllib.parse.urlsplit(base)
    if parts.hostname not in LOCAL_HOSTS:
        sys.exit(f"FIXTURE_XTREAM_URL must be the local bridge (127.0.0.1 or localhost), not {parts.hostname}")
    if not user or not password:
        sys.exit("Set FIXTURE_XTREAM_USER and FIXTURE_XTREAM_PASS to the bridge login")

    cleaner = Cleaner([(password, "pass"), (user, "user"), (parts.netloc, "provider.invalid"), (parts.hostname, "provider.invalid")])

    def api(action=None, **params):
        query = {"username": user, "password": password, **({"action": action} if action else {}), **params}
        return json.loads(get(f"{base}/player_api.php?{urllib.parse.urlencode(query)}"))

    login = api()
    if login.get("user_info", {}).get("auth") != 1:
        sys.exit("The bridge refused the login")
    answers = {"login": ("login", login)}
    for kind, prefix in (("vod", "vod"), ("series", "series"), ("live", "live")):
        answers[f"get_{prefix}_categories"] = (kind, trim(api(f"get_{prefix}_categories")))
    answers["get_vod_streams"] = ("vod", trim(api("get_vod_streams")))
    answers["get_series"] = ("series", trim(api("get_series")))
    answers["get_live_streams"] = ("live", trim(api("get_live_streams")))
    films, shows, channels = (answers[k][1] for k in ("get_vod_streams", "get_series", "get_live_streams"))
    if films:
        answers["get_vod_info"] = ("vod", api("get_vod_info", vod_id=films[0]["stream_id"]))
    if shows:
        answers["get_series_info"] = ("series", trim_series_info(api("get_series_info", series_id=shows[0]["series_id"])))
    if channels:
        stream_id = channels[0]["stream_id"]
        answers["get_short_epg"] = ("live", trim_guide(api("get_short_epg", stream_id=stream_id, limit=GUIDE_ITEMS)))
        answers["get_simple_data_table"] = ("live", trim_guide(api("get_simple_data_table", stream_id=stream_id)))

    folder = scratch / "xtream"
    folder.mkdir(parents=True)
    for action, (kind, answer) in answers.items():
        cleaned = clean_json(answer, cleaner, [kind])
        (folder / f"rec-{action}.json").write_text(json.dumps(cleaned, indent=1, ensure_ascii=False) + "\n")
    if channels:
        xml = get(f"{base}/xmltv.php?{urllib.parse.urlencode({'username': user, 'password': password})}")
        (scratch / "xmltv").mkdir(parents=True)
        (scratch / "xmltv" / "rec-xmltv.xml").write_text(clean_xmltv(xml, cleaner))
    return sorted(answers)


def clean_xmltv(xml, cleaner):
    """The first channels of a guide with their first programmes, cleaned."""
    tv = ET.fromstring(xml)
    channels = tv.findall("channel")[:LIST_ITEMS]
    kept_ids = {c.get("id") for c in channels}
    programmes = [p for p in tv.findall("programme") if p.get("channel") in kept_ids][: LIST_ITEMS * 2]
    for element in list(tv):
        if element not in channels and element not in programmes:
            tv.remove(element)
    for element in tv.iter():
        for attribute in ("id", "channel"):
            if element.get(attribute):
                element.set(attribute, cleaner.text(element.get(attribute)))
        if element.get("src"):
            element.set("src", cleaner.url(element.get("src"), True))
        if element.tag == "display-name":
            element.text = cleaner.title(element.text or "", "Channel")
        if element.tag == "title":
            element.text = cleaner.title(element.text or "", "Programme")
        if element.tag == "desc":
            element.text = cleaner.plot(element.text or "")
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(tv, encoding="unicode") + "\n"


M3U_ATTRIBUTE = re.compile(r'([\w-]+)="([^"]*)"')


def m3u_entries(text):
    """(#EXTINF line, its other # lines, stream URL) for each entry of a playlist."""
    entries, pending = [], None
    for line in text.splitlines()[1:]:
        if line.startswith("#EXTINF"):
            pending = [line, []]
        elif line.startswith("#") and pending:
            pending[1].append(line)
        elif line.strip() and pending:
            entries.append((pending[0], pending[1], line.strip()))
            pending = None
    return entries


def m3u_shape(entry):
    extinf, options, url = entry
    names = tuple(sorted(name for name, _ in M3U_ATTRIBUTE.findall(extinf)))
    option_names = tuple(sorted(o.split("=")[0] for o in options))
    path = urllib.parse.urlsplit(url).path.lower()
    return names, option_names, path.rsplit(".", 1)[-1] if "." in path.rsplit("/", 1)[-1] else "", bool(urllib.parse.urlsplit(url).query)


def clean_m3u_line(line, cleaner):
    def attribute(match):
        name, value = match.groups()
        cleaned = cleaner.url(value, name == "tvg-logo") if value.startswith(("http://", "https://")) else value
        return f'{name}="{cleaned}"'

    line = M3U_ATTRIBUTE.sub(attribute, line)
    return re.sub(r"(http-referrer=)(https?://\S+)", lambda m: m.group(1) + cleaner.url(m.group(2), False), line)


def record_m3u(scratch):
    text = get(M3U_URL)
    header = text.splitlines()[0]
    if not header.startswith("#EXTM3U"):
        sys.exit("The iptv-org playlist has no #EXTM3U header")
    cleaner = Cleaner([])
    # An address in a path or a query would stay after the host is cleaned: such entries are left out.
    candidates = [e for e in m3u_entries(text) if not IPV4.search(urllib.parse.urlsplit(e[2]).path + urllib.parse.urlsplit(e[2]).query)]
    shapes, kept = set(), []
    for entry in candidates:
        shape = m3u_shape(entry)
        if shape not in shapes and len(kept) < M3U_ITEMS:
            kept.append(entry)
        shapes.add(shape)
    lines = [clean_m3u_line(header, cleaner)]
    for extinf, options, url in kept:
        lines += [clean_m3u_line(extinf, cleaner)] + [clean_m3u_line(o, cleaner) for o in options] + [cleaner.url(url, False)]
    (scratch / "m3u").mkdir(parents=True)
    (scratch / "m3u" / "rec-index.m3u").write_text("\n".join(lines) + "\n")
    return [f"{len(kept)} playlist entries"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--source", choices=["xtream", "m3u", "all"], default="all")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()

    scratch = Path(tempfile.mkdtemp(prefix="provider-fixtures-"))
    recorded = []
    if args.source in ("xtream", "all"):
        recorded += record_xtream(scratch)
    if args.source in ("m3u", "all"):
        recorded += record_m3u(scratch)

    gate = subprocess.run([str(GATE), str(scratch)])
    if gate.returncode != 0:
        sys.exit(f"The privacy gate failed: nothing written. The cleaned files are in {scratch}")

    for folder in sorted(p for p in scratch.iterdir() if p.is_dir()):
        target = args.out / folder.name if args.out else DESTINATIONS[folder.name]
        target.mkdir(parents=True, exist_ok=True)
        for file in sorted(folder.iterdir()):
            shutil.copy(file, target / file.name)
            print(f"wrote {target / file.name}")
    shutil.rmtree(scratch)
    print(f"Recorded: {', '.join(recorded)}. Read the diff before a commit.")


if __name__ == "__main__":
    main()
