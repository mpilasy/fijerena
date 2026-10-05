#!/usr/bin/env python3
"""
Exposes a Jellyfin server as an Xtream Codes panel, for testing Fijerena's Xtream provider.

Stdlib only. The app's Xtream username/password are passed through to Jellyfin: the bridge holds
no credentials of its own, so any Jellyfin user can log in and sees their own libraries. Movies and shows come from Jellyfin's movie/tvshow libraries (one Xtream category
per library); live channels and EPG come from Jellyfin Live TV if it has any. Playback is a 302
redirect to Jellyfin's static stream, so Jellyfin must be reachable from the playing device at
JELLYFIN_URL.

Xtream ids are integers; Jellyfin's are GUIDs. The mapping lives in a small SQLite file so ids
stay stable across restarts (watch history and favourites key on them).

Config (env):
  JELLYFIN_URL       e.g. http://192.168.1.10:8096   (required; use a LAN address, not localhost)
  BRIDGE_PORT        listen port                      (default: 8080)
  BRIDGE_DB          id map file                      (default: ./xtream_ids_<jellyfin host>.db)
  CACHE_TTL          seconds to cache catalog lists   (default: 300)

Test mode for shared logins (docs/plans/20261005_shared-logins-plan.md), all off by default:
  BRIDGE_TEST_PASSWORD   extra logins: username "<jellyfin user>+<anything>" (e.g. "tahiry+2") with
                         this password signs in as that Jellyfin user, once the user itself has
                         signed in through the bridge since it started. Same catalogue, same ids,
                         counted as its own account.
  BRIDGE_MAX_CONNECTIONS streams one Xtream username may hold (reported as max_connections); a
                         stream request over it gets HTTP 458, as IPTV panels answer.
  BRIDGE_HOLD_SECONDS    how long a stream request holds its connection (default: 90): the bridge
                         only redirects, so it can't see a stream stop. Like a panel's stale count.
  BRIDGE_HIDE_CONS       1: always report active_cons 0, so a client must handle the 458 itself.
"""

import base64
import json
import os
import re
import sqlite3
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from xml.sax.saxutils import escape

JF_URL = os.environ["JELLYFIN_URL"].rstrip("/")
PORT = int(os.environ.get("BRIDGE_PORT", "8080"))
DB_PATH = os.environ.get("BRIDGE_DB", f"xtream_ids_{urllib.parse.urlparse(JF_URL).hostname}.db")
CACHE_TTL = int(os.environ.get("CACHE_TTL", "300"))
TEST_PASSWORD = os.environ.get("BRIDGE_TEST_PASSWORD")
MAX_CONNECTIONS = int(os.environ.get("BRIDGE_MAX_CONNECTIONS", "0"))
HOLD_SECONDS = int(os.environ.get("BRIDGE_HOLD_SECONDS", "90"))
HIDE_CONS = os.environ.get("BRIDGE_HIDE_CONS") == "1"

AUTH_BASE = 'MediaBrowser Client="xtream-bridge", Device="xtream-bridge", DeviceId="xtream-bridge", Version="1.0"'
LIST_FIELDS = "Overview,Genres,ProviderIds,DateCreated,DateLastMediaAdded,PremiereDate"
INFO_FIELDS = "Overview,Genres,People,ProviderIds,DateCreated,PremiereDate,MediaSources"


# --- Jellyfin client -------------------------------------------------------------------------

class Jellyfin:
    def __init__(self, username, password):
        self.username = username
        self.password = password
        self.token = None
        self.user_id = None
        self.lock = threading.Lock()

    def login(self):
        body = json.dumps({"Username": self.username, "Pw": self.password}).encode()
        req = urllib.request.Request(
            f"{JF_URL}/Users/AuthenticateByName", data=body, method="POST",
            headers={"Authorization": AUTH_BASE, "Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.load(r)
        self.token = data["AccessToken"]
        self.user_id = data["User"]["Id"]

    def request(self, path, params=None, method="GET", body=None):
        with self.lock:
            if self.token is None:
                self.login()
        url = f"{JF_URL}{path}"
        if params:
            url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
        data = json.dumps(body).encode() if body is not None else None
        for attempt in range(2):
            req = urllib.request.Request(url, data=data, method=method, headers={
                "Authorization": f'{AUTH_BASE}, Token="{self.token}"',
                "Content-Type": "application/json",
            })
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    raw = r.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as e:
                if e.code == 401 and attempt == 0:
                    with self.lock:
                        self.login()
                    continue
                raise

    def items(self, **params):
        params.setdefault("Recursive", "true")
        return self.request(f"/Users/{self.user_id}/Items", params)["Items"]

    def stream_url(self, item_id, **extra):
        q = {"static": "true", "api_key": self.token, **extra}
        return f"{JF_URL}/Videos/{item_id}/stream?{urllib.parse.urlencode(q)}"


_sessions = {}
_sessions_lock = threading.Lock()


def session(username, password):
    """The logged-in Jellyfin session for these credentials, or None if Jellyfin rejects them."""
    if TEST_PASSWORD and "+" in username and password == TEST_PASSWORD:
        base = username.split("+", 1)[0]
        with _sessions_lock:
            return next((v for (u, _), v in _sessions.items() if u == base), None)
    key = (username, password)
    with _sessions_lock:
        if key in _sessions:
            return _sessions[key]
    s = Jellyfin(username, password)
    try:
        s.login()
    except urllib.error.HTTPError as e:
        if e.code == 401:
            return None
        raise
    with _sessions_lock:
        _sessions[key] = s
    return s


_holds = {}  # Xtream username -> expiry times of the streams it holds (test mode)
_holds_lock = threading.Lock()


def active_cons(username):
    now = time.time()
    with _holds_lock:
        _holds[username] = [t for t in _holds.get(username, []) if t > now]
        return len(_holds[username])


def take_connection(username):
    """False when the username already holds BRIDGE_MAX_CONNECTIONS streams."""
    if MAX_CONNECTIONS <= 0:
        return True
    if active_cons(username) >= MAX_CONNECTIONS:
        return False
    with _holds_lock:
        _holds[username].append(time.time() + HOLD_SECONDS)
    return True


class _Current(threading.local):
    session = None


_current = _Current()


class _CurrentSession:
    """Stands in for the session of the request this thread is serving (one thread per request)."""

    def __getattr__(self, name):
        return getattr(_current.session, name)


jf = _CurrentSession()


# --- id map ----------------------------------------------------------------------------------

class IdMap:
    def __init__(self, path):
        self.db = sqlite3.connect(path, check_same_thread=False)
        self.db.execute("CREATE TABLE IF NOT EXISTS ids (n INTEGER PRIMARY KEY AUTOINCREMENT, guid TEXT UNIQUE)")
        self.db.commit()
        self.lock = threading.Lock()
        self.to_int = {}
        self.to_guid = {}
        for n, guid in self.db.execute("SELECT n, guid FROM ids"):
            self.to_int[guid] = n
            self.to_guid[n] = guid

    def num(self, guid):
        n = self.to_int.get(guid)
        if n is not None:
            return n
        with self.lock:
            if guid not in self.to_int:
                n = self.db.execute("INSERT INTO ids (guid) VALUES (?)", (guid,)).lastrowid
                self.db.commit()
                self.to_int[guid] = n
                self.to_guid[n] = guid
            return self.to_int[guid]

    def guid(self, n):
        return self.to_guid.get(int(n))


ids = IdMap(DB_PATH)


# --- caching ---------------------------------------------------------------------------------

_cache = {}
_cache_lock = threading.Lock()


def cached(key, fn):
    key = (jf.user_id, key)  # libraries differ per user
    with _cache_lock:
        hit = _cache.get(key)
        if hit and time.time() - hit[0] < CACHE_TTL:
            return hit[1]
    value = fn()
    with _cache_lock:
        _cache[key] = (time.time(), value)
    return value


# --- mapping helpers -------------------------------------------------------------------------

def parse_jf_date(s):
    """Jellyfin dates carry 7 fractional digits, which fromisoformat rejects."""
    if not s:
        return None
    s = re.sub(r"(\.\d{6})\d+", r"\1", s).replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(s)
    except ValueError:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def epoch(s):
    dt = parse_jf_date(s)
    return int(dt.timestamp()) if dt else None


def image(item_id, kind="Primary"):
    return f"{JF_URL}/Items/{item_id}/Images/{kind}"


def backdrops(item):
    return [image(item["Id"], "Backdrop")] if item.get("BackdropImageTags") else []


def container(item):
    c = item.get("Container") or ""
    if not c and item.get("MediaSources"):
        c = item["MediaSources"][0].get("Container") or ""
    return c.split(",")[0] or "mp4"


def people(item, kind):
    return ", ".join(p["Name"] for p in item.get("People", []) if p.get("Type") == kind)


def secs(item):
    ticks = item.get("RunTimeTicks")
    return int(ticks / 10_000_000) if ticks else 0


def hms(total):
    return f"{total // 3600:02d}:{total % 3600 // 60:02d}:{total % 60:02d}"


def rating(item):
    r = item.get("CommunityRating")
    return round(r, 1) if r else 0


def tmdb(item):
    return (item.get("ProviderIds") or {}).get("Tmdb", "")


def release_date(item):
    dt = parse_jf_date(item.get("PremiereDate"))
    return dt.strftime("%Y-%m-%d") if dt else ""


# --- catalog ---------------------------------------------------------------------------------

def libraries(kind):
    views = cached("views", lambda: jf.request(f"/Users/{jf.user_id}/Views")["Items"])
    return [v for v in views if v.get("CollectionType") == kind]


def categories(kind):
    return [{"category_id": str(ids.num(v["Id"])), "category_name": v["Name"], "parent_id": 0}
            for v in libraries(kind)]


def library_items(kind, item_type):
    def load():
        # An item in two libraries comes back from both with the same Id. Xtream gives each stream
        # one category, and a client keeps whichever copy it reads last (the app saw such movies
        # switch category on every sync), so list it once, under the first library by name.
        out, seen = [], set()
        for lib in sorted(libraries(kind), key=lambda v: (v["Name"], v["Id"])):
            for it in jf.items(ParentId=lib["Id"], IncludeItemTypes=item_type, Fields=LIST_FIELDS,
                               SortBy="SortName"):
                if it["Id"] in seen:
                    continue
                seen.add(it["Id"])
                out.append((str(ids.num(lib["Id"])), it))
        return out
    return cached(f"items:{item_type}", load)


def vod_streams(category_id):
    out = []
    for i, (cat, it) in enumerate(library_items("movies", "Movie"), 1):
        if category_id and cat != category_id:
            continue
        out.append({
            "num": i,
            "name": it["Name"],
            "stream_type": "movie",
            "stream_id": ids.num(it["Id"]),
            "stream_icon": image(it["Id"]),
            "rating": rating(it),
            "rating_5based": round(rating(it) / 2, 1),
            "added": str(epoch(it.get("DateCreated")) or ""),
            "category_id": cat,
            "container_extension": container(it),
            "custom_sid": "",
            "direct_source": "",
            "plot": it.get("Overview", ""),
            "genre": ", ".join(it.get("Genres", [])),
            "release_date": release_date(it),
            "tmdb": tmdb(it),
        })
    return out


def series_list(category_id):
    out = []
    for i, (cat, it) in enumerate(library_items("tvshows", "Series"), 1):
        if category_id and cat != category_id:
            continue
        out.append({
            "num": i,
            "name": it["Name"],
            "series_id": ids.num(it["Id"]),
            "cover": image(it["Id"]),
            "plot": it.get("Overview", ""),
            "cast": "",
            "director": "",
            "genre": ", ".join(it.get("Genres", [])),
            "releaseDate": release_date(it),
            # Moves when an episode is added, like a real panel's last_modified (DateCreated never does).
            "last_modified": str(epoch(it.get("DateLastMediaAdded") or it.get("DateCreated")) or ""),
            "rating": rating(it),
            "rating_5based": round(rating(it) / 2, 1),
            "backdrop_path": backdrops(it),
            "youtube_trailer": "",
            "episode_run_time": "",
            "category_id": cat,
            "tmdb": tmdb(it),
        })
    return out


def vod_info(vod_id):
    guid = ids.guid(vod_id)
    if not guid:
        return []
    it = jf.request(f"/Users/{jf.user_id}/Items/{guid}", {"Fields": INFO_FIELDS})
    src = (it.get("MediaSources") or [{}])[0]
    streams = src.get("MediaStreams") or []
    video = next((s for s in streams if s.get("Type") == "Video"), None)
    audio = next((s for s in streams if s.get("Type") == "Audio"), None)
    d = secs(it)
    return {
        "info": {
            "name": it["Name"],
            "movie_image": image(guid),
            "cover_big": image(guid),
            "backdrop_path": backdrops(it),
            "plot": it.get("Overview", ""),
            "cast": people(it, "Actor"),
            "director": people(it, "Director"),
            "genre": ", ".join(it.get("Genres", [])),
            "release_date": release_date(it),
            "rating": rating(it),
            "duration_secs": d,
            "duration": hms(d),
            "tmdb_id": tmdb(it),
            "youtube_trailer": "",
            "video": {"width": video.get("Width"), "height": video.get("Height"),
                      "codec_name": video.get("Codec")} if video else [],
            "audio": {"codec_name": audio.get("Codec"), "language": audio.get("Language")} if audio else [],
            "bitrate": int((src.get("Bitrate") or 0) / 1000),
        },
        "movie_data": {
            "stream_id": int(vod_id),
            "name": it["Name"],
            "added": str(epoch(it.get("DateCreated")) or ""),
            "container_extension": container(it),
            "custom_sid": "",
            "direct_source": "",
        },
    }


def series_info(series_id):
    guid = ids.guid(series_id)
    if not guid:
        return []
    show = jf.request(f"/Users/{jf.user_id}/Items/{guid}", {"Fields": INFO_FIELDS})
    seasons = jf.request(f"/Shows/{guid}/Seasons", {"userId": jf.user_id})["Items"]
    episodes = jf.request(f"/Shows/{guid}/Episodes", {
        "userId": jf.user_id, "Fields": "Overview,MediaSources,PremiereDate,ProviderIds,DateCreated",
    })["Items"]

    by_season = {}
    for ep in episodes:
        season = ep.get("ParentIndexNumber") or 0
        d = secs(ep)
        by_season.setdefault(str(season), []).append({
            "id": str(ids.num(ep["Id"])),
            "episode_num": ep.get("IndexNumber") or 0,
            "title": ep["Name"],
            "container_extension": container(ep),
            "season": season,
            "added": str(epoch(ep.get("DateCreated")) or ""),
            "custom_sid": "",
            "direct_source": "",
            "info": {
                "name": ep["Name"],
                "plot": ep.get("Overview", ""),
                "air_date": release_date(ep),
                "movie_image": image(ep["Id"]),
                "duration_secs": d,
                "duration": hms(d),
                "rating": rating(ep),
                "tmdb_id": tmdb(ep),
            },
        })

    counts = {k: len(v) for k, v in by_season.items()}
    return {
        "seasons": [{
            "season_number": s.get("IndexNumber") or 0,
            "name": s["Name"],
            "episode_count": counts.get(str(s.get("IndexNumber") or 0), 0),
            "cover": image(s["Id"]),
            "air_date": release_date(s),
            "overview": s.get("Overview", ""),
            "id": ids.num(s["Id"]),
        } for s in seasons],
        "info": {
            "name": show["Name"],
            "cover": image(guid),
            "plot": show.get("Overview", ""),
            "cast": people(show, "Actor"),
            "director": people(show, "Director"),
            "genre": ", ".join(show.get("Genres", [])),
            "release_date": release_date(show),
            "rating": rating(show),
            "rating_5based": round(rating(show) / 2, 1),
            "backdrop_path": backdrops(show),
            "youtube_trailer": "",
            "episode_run_time": "",
            "category_id": "",
            "tmdb": tmdb(show),
        },
        "episodes": by_season,
    }


# --- live TV + EPG ---------------------------------------------------------------------------

LIVE_CATEGORY = "1"


def live_channels():
    def load():
        try:
            return jf.request("/LiveTv/Channels", {"userId": jf.user_id, "SortBy": "SortName"})["Items"]
        except urllib.error.HTTPError:
            return []  # Live TV not set up
    return cached("channels", load)


def live_categories():
    return [{"category_id": LIVE_CATEGORY, "category_name": "Live TV", "parent_id": 0}] if live_channels() else []


def live_streams(category_id):
    if category_id and category_id != LIVE_CATEGORY:
        return []
    return [{
        "num": i,
        "name": ch["Name"],
        "stream_type": "live",
        "stream_id": ids.num(ch["Id"]),
        "stream_icon": image(ch["Id"]) if ch.get("ImageTags", {}).get("Primary") else "",
        "epg_channel_id": ch["Id"],
        "added": "",
        "category_id": LIVE_CATEGORY,
        "custom_sid": "",
        "tv_archive": 0,
        "direct_source": "",
        "tv_archive_duration": 0,
    } for i, ch in enumerate(live_channels(), 1)]


def programs(channel_guids=None, limit=None):
    params = {
        "UserId": jf.user_id,
        "MinEndDate": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "SortBy": "StartDate",
        "Fields": "Overview",
    }
    if channel_guids:
        params["ChannelIds"] = ",".join(channel_guids)
    if limit:
        params["Limit"] = limit
    try:
        return jf.request("/LiveTv/Programs", params)["Items"]
    except urllib.error.HTTPError:
        return []


def b64(s):
    return base64.b64encode((s or "").encode()).decode()


def epg_listings(stream_id, limit=None):
    guid = ids.guid(stream_id)
    if not guid:
        return {"epg_listings": []}
    now = time.time()
    out = []
    for p in programs([guid], limit):
        start, end = epoch(p["StartDate"]), epoch(p["EndDate"])
        out.append({
            "id": p["Id"],
            "epg_id": guid,
            "title": b64(p.get("Name")),
            "lang": "",
            "start": datetime.fromtimestamp(start, timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
            "end": datetime.fromtimestamp(end, timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
            "description": b64(p.get("Overview")),
            "channel_id": guid,
            "start_timestamp": str(start),
            "stop_timestamp": str(end),
            "now_playing": 1 if start <= now < end else 0,
            "has_archive": 0,
        })
    return {"epg_listings": out}


def xmltv():
    def fmt(s):
        return parse_jf_date(s).strftime("%Y%m%d%H%M%S +0000")

    lines = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="xtream-bridge">']
    for ch in live_channels():
        lines.append(f'  <channel id="{escape(ch["Id"])}"><display-name>{escape(ch["Name"])}</display-name></channel>')
    for p in programs():
        lines.append(
            f'  <programme start="{fmt(p["StartDate"])}" stop="{fmt(p["EndDate"])}" channel="{escape(p["ChannelId"])}">'
            f'<title>{escape(p.get("Name") or "")}</title>'
            f'<desc>{escape(p.get("Overview") or "")}</desc></programme>'
        )
    lines.append("</tv>")
    return "\n".join(lines)


def live_redirect(guid):
    """Open the channel through PlaybackInfo; Jellyfin won't static-stream a channel otherwise."""
    info = jf.request(f"/Items/{guid}/PlaybackInfo", {"UserId": jf.user_id}, method="POST", body={
        "UserId": jf.user_id, "AutoOpenLiveStream": True, "IsPlayback": True,
        "DeviceProfile": {"DirectPlayProfiles": [{"Type": "Video"}]},
    })
    src = info["MediaSources"][0]
    path = src.get("Path") or ""
    if src.get("IsRemote") and path.startswith("http"):
        return path  # M3U tuner: hand out the upstream URL directly
    return jf.stream_url(guid, MediaSourceId=src["Id"], LiveStreamId=src.get("LiveStreamId"),
                         PlaySessionId=info.get("PlaySessionId"))


# --- HTTP ------------------------------------------------------------------------------------

def server_info(host, username, password):
    hostname, _, port = host.partition(":")
    now = int(time.time())
    cons = 0 if HIDE_CONS else active_cons(username)
    return {
        "user_info": {
            "username": username, "password": password, "message": "", "auth": 1, "status": "Active",
            "exp_date": str(now + 365 * 86400), "is_trial": "0", "active_cons": str(cons),
            "created_at": str(now), "max_connections": str(MAX_CONNECTIONS or 5),
            "allowed_output_formats": ["m3u8", "ts"],
        },
        "server_info": {
            "url": hostname, "port": port or "80", "https_port": "", "server_protocol": "http",
            "rtmp_port": "", "timezone": "UTC", "timestamp_now": now,
            "time_now": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S"),
        },
    }


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send(self, code, body, ctype="application/json", headers=None):
        if not isinstance(body, (bytes, str)):
            body = json.dumps(body)
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def redirect(self, url):
        self.send(302, b"", "text/plain", {"Location": url})

    def authed(self, user, password):
        _current.session = session(user, password) if user else None
        return _current.session is not None

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        try:
            self.route()
        except urllib.error.HTTPError as e:
            self.send(502, {"error": f"jellyfin: HTTP {e.code} {e.reason}"})
        except Exception as e:  # keep serving; a test bridge should say what broke, not die
            self.send(500, {"error": f"{type(e).__name__}: {e}"})

    def route(self):
        url = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        path = url.path

        if path in ("/player_api.php", "/get.php", "/xmltv.php"):
            if not self.authed(q.get("username"), q.get("password")):
                return self.send(401 if path != "/player_api.php" else 200,
                                 {"user_info": {"auth": 0}} if path == "/player_api.php" else "unauthorized")
            if path == "/player_api.php":
                return self.send(200, self.api(q.get("action"), q))
            if path == "/xmltv.php":
                return self.send(200, xmltv(), "application/xml; charset=utf-8")
            return self.send(200, self.m3u(), "audio/x-mpegurl; charset=utf-8")

        m = re.fullmatch(r"/(live|movie|series)/([^/]+)/([^/]+)/(\d+)(?:\.\w+)?", path)
        if not m:
            return self.send(404, {"error": "not found"})
        kind, user, password, num = m.groups()
        if not self.authed(urllib.parse.unquote(user), urllib.parse.unquote(password)):
            return self.send(401, "unauthorized", "text/plain")
        guid = ids.guid(num)
        if not guid:
            return self.send(404, {"error": "unknown id"})
        if not take_connection(urllib.parse.unquote(user)):
            return self.send(458, {"error": "max connections reached"})
        return self.redirect(live_redirect(guid) if kind == "live" else jf.stream_url(guid))

    def api(self, action, q):
        cat = q.get("category_id")
        if action is None:
            return server_info(self.headers.get("Host", f"localhost:{PORT}"), q.get("username"), q.get("password"))
        if action == "get_live_categories":
            return live_categories()
        if action == "get_live_streams":
            return live_streams(cat)
        if action == "get_vod_categories":
            return categories("movies")
        if action == "get_vod_streams":
            return vod_streams(cat)
        if action == "get_vod_info":
            return vod_info(q.get("vod_id", 0))
        if action == "get_series_categories":
            return categories("tvshows")
        if action == "get_series":
            return series_list(cat)
        if action == "get_series_info":
            return series_info(q.get("series_id", 0))
        if action == "get_short_epg":
            return epg_listings(q.get("stream_id", 0), int(q.get("limit", 4)))
        if action == "get_simple_data_table":
            return epg_listings(q.get("stream_id", 0))
        return []

    def m3u(self):
        host = self.headers.get("Host", f"localhost:{PORT}")
        base = f"http://{host}"
        user, password = (urllib.parse.quote(v, safe="") for v in (jf.username, jf.password))
        lines = ["#EXTM3U"]
        for s in live_streams(None):
            lines.append(f'#EXTINF:-1 tvg-id="{s["epg_channel_id"]}" tvg-name="{s["name"]}" '
                         f'tvg-logo="{s["stream_icon"]}" group-title="Live TV",{s["name"]}')
            lines.append(f"{base}/live/{user}/{password}/{s['stream_id']}.ts")
        names = {c["category_id"]: c["category_name"] for c in categories("movies")}
        for s in vod_streams(None):
            lines.append(f'#EXTINF:-1 tvg-name="{s["name"]}" tvg-logo="{s["stream_icon"]}" '
                         f'group-title="{names.get(s["category_id"], "")}",{s["name"]}')
            lines.append(f"{base}/movie/{user}/{password}/{s['stream_id']}.{s['container_extension']}")
        return "\n".join(lines) + "\n"

    def log_message(self, fmt, *args):
        # Credentials ride in every Xtream URL; keep them out of the log.
        line = re.sub(r"(password=)[^&\s]*", r"\1***", fmt % args)
        line = re.sub(r"(/(?:live|movie|series)/[^/]+/)[^/]+/", r"\1***/", line)
        print(f"{self.address_string()} {line}", flush=True)


if __name__ == "__main__":
    print(f"Bridging Jellyfin {JF_URL} on :{PORT} (id map: {DB_PATH})", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
