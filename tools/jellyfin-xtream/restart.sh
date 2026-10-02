#!/usr/bin/env bash
# (Re)starts the Jellyfin-to-Xtream bridge in the background, detached from this shell.
#
#   ./restart.sh                                   # sm.njarasoa.org on :8080
#   JELLYFIN_URL=https://other.host BRIDGE_PORT=8081 ./restart.sh
#
# One instance per port: the previous one on the same port is stopped first. Log goes to
# bridge-<port>.log next to this script.
set -euo pipefail
cd "$(dirname "$0")"

export JELLYFIN_URL="${JELLYFIN_URL:-https://sm.njarasoa.org}"
export BRIDGE_PORT="${BRIDGE_PORT:-8080}"
pidfile="bridge-$BRIDGE_PORT.pid"
log="bridge-$BRIDGE_PORT.log"

if [[ -f "$pidfile" ]] && kill "$(cat "$pidfile")" 2>/dev/null; then
    echo "Stopped bridge $(cat "$pidfile")"
    sleep 1
fi

setsid nohup python3 xtream_bridge.py > "$log" 2>&1 < /dev/null &
echo $! > "$pidfile"

for _ in {1..10}; do
    if curl -s -o /dev/null "http://127.0.0.1:$BRIDGE_PORT/player_api.php"; then
        echo "Bridge $(cat "$pidfile") up on :$BRIDGE_PORT for $JELLYFIN_URL (log: $log)"
        exit 0
    fi
    sleep 0.5
done
echo "Bridge did not come up; see $log" >&2
exit 1
