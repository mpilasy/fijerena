#!/usr/bin/env python3
"""
A fake Xtream Codes panel with fixed data, for the nightly TV focus walk and other scripted checks.

Stdlib only. The data never changes: the same categories, channels, films and series with the same
ids on every start, so a walk file can name what it expects to see. Only the guide moves: its
programmes are made around the current time, so the guide is never empty.

Login: username "test", password "test". Any other login gets {"user_info": {"auth": 0}}, as a
real panel answers.

Serves:
  /player_api.php          user and server info (no action), and the actions get_live_categories,
                           get_live_streams, get_vod_categories, get_vod_streams, get_vod_info,
                           get_series_categories, get_series, get_series_info, get_short_epg and
                           get_simple_data_table (category_id filters the lists)
  /xmltv.php               the XMLTV guide of the live channels
  /live/, /movie/, /series/ stream URLs: always HTTP 404. The walks never play anything.

Usage:
  python3 tools/fake-xtream/server.py [--port 8090]   serve until stopped
  python3 tools/fake-xtream/server.py --self-test     start on a free port, call every endpoint,
                                                      check the answers, exit 0 (pass) or 1 (fail)
"""

import argparse
import base64
import json
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from xml.sax.saxutils import escape

USERNAME = "test"
PASSWORD = "test"

# A fixed "added" time (2026-01-01 00:00 UTC), so the lists are the same on every start.
ADDED = "1767225600"

LIVE_CATEGORIES = [("101", "News"), ("102", "Kids"), ("103", "Adult")]
VOD_CATEGORIES = [("201", "Drama"), ("202", "Kids"), ("203", "Adult")]
SERIES_CATEGORIES = [("301", "Drama Series"), ("302", "Kids Series")]

# (stream_id, name, category_id, epg_channel_id)
CHANNELS = [
    (1001, "World News", "101", "worldnews.fake"),
    (1002, "City News", "101", "citynews.fake"),
    (1003, "Cartoon Club", "102", "cartoonclub.fake"),
    (1004, "Story Time", "102", "storytime.fake"),
    (1005, "Late Night One", "103", "latenight1.fake"),
    (1006, "Late Night Two", "103", "latenight2.fake"),
]

# (stream_id, name, category_id, year, genre)
FILMS = [
    (2001, "The Quiet Harbour", "201", 2019, "Drama"),
    (2002, "Northern Lights", "201", 2021, "Drama"),
    (2003, "The Long Road Home", "201", 2018, "Drama"),
    (2004, "Paper Boats", "202", 2020, "Animation"),
    (2005, "The Little Robot", "202", 2022, "Animation"),
    (2006, "Forest Friends", "202", 2017, "Family"),
    (2007, "After Midnight", "203", 2016, "Thriller"),
    (2008, "Velvet Room", "203", 2015, "Thriller"),
]

# (series_id, name, category_id, year, genre)
SERIES = [
    (3001, "Harbour Town", "301", 2020, "Drama"),
    (3002, "The Firm", "301", 2022, "Drama"),
    (3003, "Space Pals", "302", 2021, "Animation"),
    (3004, "Garden Gang", "302", 2019, "Animation"),
]
SEASONS = 2
EPISODES_PER_SEASON = 3

# The guide: half-hour programmes from two hours before now to twelve hours after.
SLOT_SECONDS = 30 * 60
GUIDE_BEFORE_SECONDS = 2 * 3600
GUIDE_AFTER_SECONDS = 12 * 3600


def categories(items):
    return [{"category_id": cid, "category_name": name, "parent_id": 0} for cid, name in items]


def live_streams(category_id):
    return [{
        "num": i,
        "name": name,
        "stream_type": "live",
        "stream_id": sid,
        "stream_icon": "",
        "epg_channel_id": epg_id,
        "added": ADDED,
        "category_id": cat,
        "custom_sid": "",
        "tv_archive": 0,
        "direct_source": "",
        "tv_archive_duration": 0,
    } for i, (sid, name, cat, epg_id) in enumerate(CHANNELS, 1) if not category_id or cat == category_id]


def plot(name):
    return f"{name} is a title of the fake test source."


def vod_streams(category_id):
    return [{
        "num": i,
        "name": name,
        "stream_type": "movie",
        "stream_id": sid,
        "stream_icon": "",
        "rating": "7",
        "rating_5based": 3.5,
        "added": ADDED,
        "category_id": cat,
        "container_extension": "mp4",
        "custom_sid": "",
        "direct_source": "",
        "plot": plot(name),
        "genre": genre,
        "release_date": f"{year}-01-01",
    } for i, (sid, name, cat, year, genre) in enumerate(FILMS, 1) if not category_id or cat == category_id]


def vod_info(vod_id):
    film = next((f for f in FILMS if str(f[0]) == str(vod_id)), None)
    if film is None:
        return []
    sid, name, _, year, genre = film
    return {
        "info": {
            "name": name,
            "movie_image": "",
            "cover_big": "",
            "plot": plot(name),
            "cast": "",
            "director": "",
            "genre": genre,
            "release_date": f"{year}-01-01",
            "rating": "7",
            "duration_secs": 5400,
            "duration": "01:30:00",
            "youtube_trailer": "",
            "video": [],
            "audio": [],
        },
        "movie_data": {
            "stream_id": sid,
            "name": name,
            "added": ADDED,
            "container_extension": "mp4",
            "custom_sid": "",
            "direct_source": "",
        },
    }


def series_list(category_id):
    return [{
        "num": i,
        "name": name,
        "series_id": sid,
        "cover": "",
        "plot": plot(name),
        "cast": "",
        "director": "",
        "genre": genre,
        "releaseDate": f"{year}-01-01",
        "last_modified": ADDED,
        "rating": "7",
        "rating_5based": 3.5,
        "backdrop_path": [],
        "youtube_trailer": "",
        "episode_run_time": "25",
        "category_id": cat,
    } for i, (sid, name, cat, year, genre) in enumerate(SERIES, 1) if not category_id or cat == category_id]


def episode_id(series_id, season, number):
    # 3001, season 2, episode 3 -> "300123": stable and unique across the series.
    return f"{series_id}{season}{number}"


def series_info(series_id):
    show = next((s for s in SERIES if str(s[0]) == str(series_id)), None)
    if show is None:
        return []
    sid, name, cat, year, genre = show
    episodes = {}
    for season in range(1, SEASONS + 1):
        episodes[str(season)] = [{
            "id": episode_id(sid, season, number),
            "episode_num": number,
            "title": f"{name} - S{season:02d}E{number:02d} - Episode {number}",
            "container_extension": "mp4",
            "season": season,
            "added": ADDED,
            "custom_sid": "",
            "direct_source": "",
            "info": {
                "name": f"Episode {number}",
                "plot": f"Season {season}, episode {number} of {name}.",
                "air_date": f"{year + season - 1}-0{number}-01",
                "movie_image": "",
                "duration_secs": 1500,
                "duration": "00:25:00",
            },
        } for number in range(1, EPISODES_PER_SEASON + 1)]
    return {
        "seasons": [{
            "season_number": season,
            "name": f"Season {season}",
            "episode_count": EPISODES_PER_SEASON,
            "cover": "",
            "air_date": f"{year + season - 1}-01-01",
            "overview": "",
            "id": sid * 10 + season,
        } for season in range(1, SEASONS + 1)],
        "info": {
            "name": name,
            "cover": "",
            "plot": plot(name),
            "cast": "",
            "director": "",
            "genre": genre,
            "release_date": f"{year}-01-01",
            "rating": "7",
            "rating_5based": 3.5,
            "backdrop_path": [],
            "youtube_trailer": "",
            "episode_run_time": "25",
            "category_id": cat,
        },
        "episodes": episodes,
    }


def programmes(now=None):
    """(channel, start, stop, title) for every channel, in half-hour slots around [now]."""
    now = int(now if now is not None else time.time())
    first = (now - GUIDE_BEFORE_SECONDS) // SLOT_SECONDS * SLOT_SECONDS
    last = now + GUIDE_AFTER_SECONDS
    out = []
    for sid, name, _, epg_id in CHANNELS:
        start = first
        while start < last:
            # The title follows the slot's clock time, so the same slot has the same title every day.
            slot = datetime.fromtimestamp(start, timezone.utc).strftime("%H:%M")
            out.append((sid, epg_id, start, start + SLOT_SECONDS, f"{name} at {slot}"))
            start += SLOT_SECONDS
    return out


def b64(text):
    return base64.b64encode(text.encode()).decode()


def epg_listings(stream_id, limit=None):
    now = time.time()
    rows = [p for p in programmes(now) if str(p[0]) == str(stream_id) and p[3] > now]
    if limit:
        rows = rows[:limit]
    return {"epg_listings": [{
        "id": f"{sid}{start}",
        "epg_id": epg_id,
        "title": b64(title),
        "lang": "en",
        "start": datetime.fromtimestamp(start, timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
        "end": datetime.fromtimestamp(stop, timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
        "description": b64(f"{title}, on the fake test source."),
        "channel_id": epg_id,
        "start_timestamp": str(start),
        "stop_timestamp": str(stop),
        "now_playing": 1 if start <= now < stop else 0,
        "has_archive": 0,
    } for sid, epg_id, start, stop, title in rows]}


def xmltv():
    def fmt(t):
        return datetime.fromtimestamp(t, timezone.utc).strftime("%Y%m%d%H%M%S +0000")

    lines = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="fake-xtream">']
    for _, name, _, epg_id in CHANNELS:
        lines.append(f'  <channel id="{escape(epg_id)}"><display-name>{escape(name)}</display-name></channel>')
    for _, epg_id, start, stop, title in programmes():
        lines.append(
            f'  <programme start="{fmt(start)}" stop="{fmt(stop)}" channel="{escape(epg_id)}">'
            f'<title lang="en">{escape(title)}</title>'
            f'<desc lang="en">{escape(title)}, on the fake test source.</desc></programme>'
        )
    lines.append("</tv>")
    return "\n".join(lines) + "\n"


def server_info(host):
    hostname, _, port = host.partition(":")
    now = int(time.time())
    return {
        "user_info": {
            "username": USERNAME, "password": PASSWORD, "message": "", "auth": 1, "status": "Active",
            # 2099-01-01: never expires.
            "exp_date": "4070908800", "is_trial": "0", "active_cons": "0",
            "created_at": ADDED, "max_connections": "2",
            "allowed_output_formats": ["m3u8", "ts"],
        },
        "server_info": {
            "url": hostname, "port": port or "80", "https_port": "", "server_protocol": "http",
            "rtmp_port": "", "timezone": "UTC", "timestamp_now": now,
            "time_now": datetime.fromtimestamp(now, timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
        },
    }


def api(action, q, host):
    cat = q.get("category_id")
    answers = {
        None: lambda: server_info(host),
        "get_live_categories": lambda: categories(LIVE_CATEGORIES),
        "get_live_streams": lambda: live_streams(cat),
        "get_vod_categories": lambda: categories(VOD_CATEGORIES),
        "get_vod_streams": lambda: vod_streams(cat),
        "get_vod_info": lambda: vod_info(q.get("vod_id")),
        "get_series_categories": lambda: categories(SERIES_CATEGORIES),
        "get_series": lambda: series_list(cat),
        "get_series_info": lambda: series_info(q.get("series_id")),
        "get_short_epg": lambda: epg_listings(q.get("stream_id"), int(q.get("limit", 4))),
        "get_simple_data_table": lambda: epg_listings(q.get("stream_id")),
    }
    return answers.get(action, lambda: [])()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send(self, code, body, ctype="application/json"):
        if not isinstance(body, (bytes, str)):
            body = json.dumps(body)
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        authed = q.get("username") == USERNAME and q.get("password") == PASSWORD
        host = self.headers.get("Host", "localhost")
        if url.path == "/player_api.php":
            self.send(200, api(q.get("action"), q, host) if authed else {"user_info": {"auth": 0}})
        elif url.path == "/xmltv.php":
            if authed:
                self.send(200, xmltv(), "application/xml; charset=utf-8")
            else:
                self.send(401, "unauthorized", "text/plain")
        else:
            # Stream URLs included: the walks never play anything.
            self.send(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)


def self_test():
    """Starts the server on a free port, calls every endpoint and checks the answers."""
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    server.RequestHandlerClass.log_message = lambda *args: None
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{server.server_address[1]}"
    failures = []

    def get(path, **params):
        query = urllib.parse.urlencode({"username": USERNAME, "password": PASSWORD, **params})
        try:
            with urllib.request.urlopen(f"{base}{path}?{query}") as answer:
                return answer.status, answer.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()

    def check(what, ok):
        print(f"{'ok  ' if ok else 'FAIL'} {what}")
        if not ok:
            failures.append(what)

    def api_json(**params):
        status, body = get("/player_api.php", **params)
        return json.loads(body) if status == 200 else None

    info = api_json()
    check("login: auth 1, Active, far expiry", info["user_info"]["auth"] == 1
          and info["user_info"]["status"] == "Active" and int(info["user_info"]["exp_date"]) > time.time() + 10 * 365 * 86400)
    check("login: server_info has a port", info["server_info"]["port"] == str(server.server_address[1]))
    status, body = get("/player_api.php", password="wrong")
    check("wrong password: auth 0", json.loads(body) == {"user_info": {"auth": 0}})

    for action, count, names in (("get_live_categories", 3, {"Kids", "Adult"}),
                                 ("get_vod_categories", 3, {"Kids", "Adult"}),
                                 ("get_series_categories", 2, {"Kids Series"})):
        rows = api_json(action=action)
        check(f"{action}: {count} categories with {sorted(names)}",
              len(rows) == count and names <= {r["category_name"] for r in rows})

    live = api_json(action="get_live_streams")
    check("get_live_streams: 6 channels with an epg_channel_id", len(live) == 6 and all(r["epg_channel_id"] for r in live))
    check("get_live_streams?category_id=102: the 2 kids' channels",
          len(api_json(action="get_live_streams", category_id="102")) == 2)
    films = api_json(action="get_vod_streams")
    check("get_vod_streams: 8 films", len(films) == 8 and all(r["stream_type"] == "movie" for r in films))
    shows = api_json(action="get_series")
    check("get_series: 4 series", len(shows) == 4)

    film = api_json(action="get_vod_info", vod_id="2004")
    check("get_vod_info 2004: Paper Boats, mp4", film["info"]["name"] == "Paper Boats"
          and film["movie_data"]["container_extension"] == "mp4")
    check("get_vod_info of an unknown id: []", api_json(action="get_vod_info", vod_id="9") == [])
    show = api_json(action="get_series_info", series_id="3003")
    check("get_series_info 3003: 2 seasons of 3 episodes", len(show["seasons"]) == 2
          and all(len(show["episodes"][s]) == 3 for s in ("1", "2")))
    all_ids = [e["id"] for s in SERIES for eps in series_info(s[0])["episodes"].values() for e in eps]
    check("episode ids are unique", len(all_ids) == len(set(all_ids)))

    short = api_json(action="get_short_epg", stream_id="1001", limit="4")
    check("get_short_epg 1001: 4 programmes, the first on now", len(short["epg_listings"]) == 4
          and short["epg_listings"][0]["now_playing"] == 1)

    status, body = get("/xmltv.php")
    tv = ET.fromstring(body)
    now = time.time()

    def epoch(text):
        return datetime.strptime(text, "%Y%m%d%H%M%S %z").timestamp()

    playing = {p.get("channel") for p in tv.iter("programme") if epoch(p.get("start")) <= now < epoch(p.get("stop"))}
    check("xmltv.php: 6 channels", status == 200 and len(tv.findall("channel")) == 6)
    check("xmltv.php: every channel has a programme on now", playing == {c[3] for c in CHANNELS})
    status, _ = get("/xmltv.php", password="wrong")
    check("xmltv.php with a wrong password: 401", status == 401)

    status, _ = get(f"/live/{USERNAME}/{PASSWORD}/1001.m3u8")
    check("a stream URL: 404 (no playback)", status == 404)

    server.shutdown()
    print("PASS" if not failures else f"FAILED: {len(failures)} checks")
    return 0 if not failures else 1


def main():
    parser = argparse.ArgumentParser(description="A fake Xtream panel with fixed data.")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--self-test", action="store_true", help="check every endpoint, then exit")
    args = parser.parse_args()
    if args.self_test:
        sys.exit(self_test())
    print(f"Fake Xtream panel on :{args.port} (login {USERNAME}/{PASSWORD})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
