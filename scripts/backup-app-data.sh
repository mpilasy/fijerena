#!/bin/bash
# Back up Fijerena's user data from one device to a .tar.gz.
# Usage: scripts/backup-app-data.sh <serial> <output.tar.gz>
#
# User data only: shared_prefs (settings, category filters, recent categories), providers.db
# (sources, profiles, guide sources, live sync) and xtream_v2_user_data.db — the watch_state,
# favorite_state and sync_* tables of xtream_v2.db. The rest of xtream_v2.db is the sources'
# catalogue, which a sync downloads again, so it is copied off with its -wal (commits not yet
# checkpointed live there), cut down to those tables on this machine, and discarded. The small
# database is written by sqlite3 here in rollback-journal mode, so it has no -wal of its own.
#
# Restore with scripts/restore-app-data.sh. Exits non-zero, writing nothing, if any step fails.
set -euo pipefail

SERIAL="${1:?usage: $0 <serial> <output.tar.gz>}"
OUT="${2:?usage: $0 <serial> <output.tar.gz>}"
PKG=org.njarasoa.fijerena
USER_TABLES=(watch_state favorite_state sync_version sync_tombstone sync_clock)

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is needed (apt install sqlite3)." >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

adb -s "$SERIAL" exec-out "run-as $PKG tar -c -C /data/data/$PKG shared_prefs \
    databases/providers.db databases/providers.db-wal databases/providers.db-shm \
    databases/xtream_v2.db databases/xtream_v2.db-wal databases/xtream_v2.db-shm 2>/dev/null" |
    tar -x -C "$STAGE" || true # tar exits non-zero when an optional -wal/-shm is absent; checked below
[ -s "$STAGE/databases/providers.db" ] || { echo "ERROR: providers.db not copied from $SERIAL." >&2; exit 1; }
[ -s "$STAGE/databases/xtream_v2.db" ] || { echo "ERROR: xtream_v2.db not copied from $SERIAL." >&2; exit 1; }

SQL="ATTACH '$STAGE/databases/xtream_v2_user_data.db' AS u;"
for t in "${USER_TABLES[@]}"; do SQL+=" CREATE TABLE u.$t AS SELECT * FROM main.$t;"; done
sqlite3 "$STAGE/databases/xtream_v2.db" "$SQL"
rm -f "$STAGE"/databases/xtream_v2.db "$STAGE"/databases/xtream_v2.db-wal "$STAGE"/databases/xtream_v2.db-shm

mkdir -p "$(dirname "$OUT")"
tar -czf "$OUT.partial" -C "$STAGE" shared_prefs databases
mv "$OUT.partial" "$OUT"
echo "Backed up $SERIAL user data -> $OUT"
