# Privacy

Fijerena is a media player for Android TV and Android phones. It does not provide or host any
content: it plays the sources you add (Xtream, Jellyfin, M3U playlists and guide feeds). This page
says what the app stores, what it sends and to whom.

## In short

- No accounts with us, no analytics, no ads, no crash reporting, no telemetry. The app has no
  server of its own and contacts no address you didn't give it, apart from TMDB when you enter
  your own TMDB key.
- Your logins stay on your device, in encrypted storage.
- Live sync, if you turn it on, goes to a sync server you choose, and everything it stores is
  end-to-end encrypted.

## What stays on your device

- **Sources and settings:** server addresses, user names, guide sources, favourites, watch
  history, profiles and preferences, in the app's private storage.
- **Passwords and keys:** source passwords, a TMDB key and the live sync account key are kept in
  Android's encrypted shared preferences, whose key lives in the Android Keystore. A guide source
  that an Xtream source adds by itself is stored without the login; the login is added only when
  the guide is downloaded.
- **Profile PINs:** an optional profile PIN is kept only as a salted hash, with the profile's
  settings (and synced with them, if you use live sync).
- **Crash log:** errors are written to a log in the app's private storage, with logins and
  passwords redacted. It leaves the device only if you share it yourself.
- **No Android backup:** the app opts out of cloud backup and of device-to-device transfer. To
  move to a new device, use Settings → Export / Import, or live sync.

## What the app sends, and where

- **Your sources.** The app connects to the servers you add, to sign in, load catalogues, play
  streams and download guides. Images (posters, channel logos) come from the addresses those
  sources give. Jellyfin servers receive a device name and a device id (Android's `ANDROID_ID`
  for this app), as every Jellyfin client sends.
- **TMDB, only with your own key.** No TMDB key is built into the app, and without one nothing is
  sent to TMDB. If you enter one (Settings → Admin → Sources & Guide → Film and Series Details), the app
  asks [The Movie Database](https://www.themoviedb.org) for artwork and details of films and
  shows, sending your key and the TMDB id of the title you open. Search also sends TMDB the text
  you type (three characters or more), to find films and shows your sources don't have, and its
  People type sends what you type there (two characters or more) to find people, films and shows
  on TMDB; a child profile never does either. With TMDB Charts on (Settings → Admin → Sources & Guide, on by default), the
  app also asks TMDB for its public chart pages (trending, popular, top rated), sending only your
  key; which of those titles your sources have is worked out on the device. For an episode's
  cast, the app asks TMDB for that episode's season, sending the show's TMDB id and the season
  number (the same request that gives the episode synopses). When you open a person from a Cast
  tab or an episode's cast, the app sends TMDB that person's TMDB id; which of their titles your
  sources have is worked out on the device. When you open one of their shows there, the app also
  sends TMDB the id of that credit, to list their episodes or seasons in the show. If TMDB refuses the
  key, the app
  stops asking until you enter another. The key is never put in an export; with live sync it goes
  to your other devices inside the encrypted records. TMDB's own privacy policy applies to those
  requests.
- **Live sync, only if you set it up.** Sources (logins included), guide sources, settings (the TMDB key included),
  profiles, favourites and watch history are sent to the sync server whose address you enter.
  Every record is encrypted on the device with AES-256-GCM, with keys derived (HKDF-SHA256) from
  an account key that only your devices hold; the server never sees it. The server sees what it
  needs to work: your devices' names, when they connect, encrypted records, and when each record
  changed or was deleted. Pairing a device passes the account key in a QR code shown on one of
  your devices, or (for a TV) sealed to a one-time key, so the server can't read it.
- **Trailers** open in the YouTube app or your browser when you choose to play one.

Plain HTTP is allowed, because many home servers and IPTV panels don't offer HTTPS. On plain
HTTP, a login and the stream travel unencrypted between the device and that server.

## Permissions

- **Internet and network state:** to reach your sources, TMDB and the sync server.
- **Camera** (phone only): only to scan a QR code when pairing a device for live sync. Nothing
  is recorded or sent.
- **Foreground services and wake lock:** to keep playback and downloads (catalogues, guides)
  running while the screen is off.

## Exports

An export file holds your settings, sources (without passwords), guide sources, favourites and
watch history, in plain JSON. An Xtream source's own guide is left out, since its address carries the login; it
is added again when the source signs in after import. Guide sources you added by hand are
exported as you typed them: if one has a password in its address, so does the file. Keep export
files private.

## Contact

Questions about privacy: open an issue in the project's repository.
