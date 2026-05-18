## 2024-05-18 - [Cleartext Traffic Disabling causes functional regressions]
**Vulnerability:** Cleartext HTTP traffic is permitted by default.
**Learning:** Disabling cleartext traffic completely breaks the IPTV player's core functionality because many IPTV providers (via Xtream Codes API, M3U playlists) only support HTTP streams and don't provide HTTPS.
**Prevention:** Do not disable cleartext traffic globally. Instead, consider allowing cleartext traffic but warning users, or selectively disabling it only for components that require high security.
