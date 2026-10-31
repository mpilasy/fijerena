#!/bin/bash
# Calls every sports endpoint the app reads once and prints OK or FAIL for each (the sports teams
# plan → Risk). None of these APIs is documented for outside use, so a break is found here by
# hand, not by users. Not run in CI: it calls outside services.
#
# OK means HTTP 200 and an answer with the field the parser reads first.
#
# Run: scripts/check-sports-apis.sh
set -uo pipefail

if ! command -v jq >/dev/null; then
    echo "jq is needed." >&2
    exit 2
fi

UA="Fijerena (Android)"
TODAY="$(date -u +%Y-%m-%d)"
TODAY_COMPACT="$(date -u +%Y%m%d)"
MONTH="$(date -u -d '+30 days' +%Y-%m-%d)"
failures=0

# check <name> <jq test> <url>
check() {
    local name="$1" test="$2" url="$3" body code
    body="$(curl -sL --compressed -A "$UA" --max-time 30 -w '\n%{http_code}' "$url")"
    code="${body##*$'\n'}"
    body="${body%$'\n'*}"
    if [ "$code" = "200" ] && jq -e "$test" >/dev/null 2>&1 <<<"$body"; then
        printf 'OK    %s\n' "$name"
    else
        printf 'FAIL  %s (HTTP %s)\n      %s\n' "$name" "$code" "$url"
        failures=$((failures + 1))
    fi
}

ESPN_SITE="https://site.api.espn.com/apis/site/v2"
ESPN_WEB="https://site.web.api.espn.com/apis"

check "ESPN team search" '.items | length > 0' "$ESPN_WEB/common/v3/search?query=arsenal&type=team&limit=10"
check "ESPN league search" '[.results[] | select(.type == "league")] | length > 0' "$ESPN_WEB/search/v2?query=premier%20league&type=league&limit=10"
check "ESPN league list (names of soccer leagues)" '.leagues | length > 0' "$ESPN_SITE/leagues/dropdown?sport=soccer&limit=500"
check "ESPN league teams"'.sports[0].leagues[0].teams | length > 0' "$ESPN_SITE/sports/hockey/nhl/teams"
check "ESPN soccer team schedule (every competition)" '.events | type == "array"' "$ESPN_SITE/sports/soccer/all/teams/359/schedule?fixture=true"
check "ESPN team schedule" '.events | length > 0' "$ESPN_SITE/sports/hockey/nhl/teams/1/schedule"
check "ESPN scoreboard (one day)" '.events | type == "array"' "$ESPN_SITE/sports/soccer/eng.1/scoreboard?dates=$TODAY_COMPACT"
check "NHL club schedule" '.games | length > 0' "https://api-web.nhle.com/v1/club-schedule-season/BOS/now"
check "MLB schedule" '.dates | type == "array"' "https://statsapi.mlb.com/api/v1/schedule?sportId=1&teamId=111&startDate=$TODAY&endDate=$MONTH&hydrate=broadcasts"

if [ "$failures" -gt 0 ]; then
    echo "$failures endpoint(s) failed."
    exit 1
fi
echo "Every endpoint answered."
