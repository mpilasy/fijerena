#!/bin/bash
# Restore Fijerena's user data to one device from a scripts/backup-app-data.sh backup.
# Usage: scripts/restore-app-data.sh <serial> <backup.tar.gz>
#
# Overwrites, on the device: shared_prefs files present in the backup, providers.db, and the
# watch_state, favorite_state and sync_* tables of xtream_v2.db. The catalogue in xtream_v2.db
# is kept as it is. The app is force-stopped first and must have been opened at least once since
# install (so its xtream_v2.db exists); it is not relaunched.
#
# Saved logins live in EncryptedSharedPreferences keyed to the device's Keystore: after an
# uninstall or `pm clear` that key is gone, the restored files can't be read, and the app resets
# them and asks for the password again.
set -euo pipefail

SERIAL="${1:?usage: $0 <serial> <backup.tar.gz>}"
BACKUP="${2:?usage: $0 <serial> <backup.tar.gz>}"
PKG=org.njarasoa.fijerena
DATA=/data/data/$PKG
USER_TABLES=(watch_state favorite_state sync_version sync_tombstone sync_clock)

command -v sqlite3 >/dev/null || { echo "ERROR: sqlite3 is needed (apt install sqlite3)." >&2; exit 1; }
[ -s "$BACKUP" ] || { echo "ERROR: no backup at $BACKUP." >&2; exit 1; }
adb -s "$SERIAL" shell pm path $PKG >/dev/null 2>&1 || { echo "ERROR: $PKG is not installed on $SERIAL." >&2; exit 1; }

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/backup" "$STAGE/device"
tar -xzf "$BACKUP" -C "$STAGE/backup"
USER_DB="$STAGE/backup/databases/xtream_v2_user_data.db"
[ -s "$STAGE/backup/databases/providers.db" ] && [ -s "$USER_DB" ] ||
    { echo "ERROR: $BACKUP is not a backup-app-data.sh backup." >&2; exit 1; }

adb -s "$SERIAL" shell am force-stop $PKG

# Merge the user tables into the device's current xtream_v2.db (catalogue untouched).
adb -s "$SERIAL" exec-out "run-as $PKG tar -c -C $DATA \
    databases/xtream_v2.db databases/xtream_v2.db-wal databases/xtream_v2.db-shm 2>/dev/null" |
    tar -x -C "$STAGE/device" || true # an absent -wal/-shm is fine; checked below
APP_DB="$STAGE/device/databases/xtream_v2.db"
[ -s "$APP_DB" ] || { echo "ERROR: no xtream_v2.db on $SERIAL — open the app once, then rerun." >&2; exit 1; }

# applying = 1 keeps the sync triggers quiet while rows go in; sync_clock is then replaced by
# the backup's own row and applying cleared. Columns are matched by name, so a backup from an
# older schema still restores into a newer one (new columns take their defaults).
SQL="PRAGMA foreign_keys = OFF; ATTACH '$USER_DB' AS b; BEGIN;
UPDATE main.sync_clock SET applying = 1;"
for t in "${USER_TABLES[@]}"; do
    COLS=$(sqlite3 "$APP_DB" "ATTACH '$USER_DB' AS b;
        SELECT group_concat('\"' || m.name || '\"', ', ') FROM pragma_table_info('$t', 'main') m
        WHERE m.name IN (SELECT name FROM pragma_table_info('$t', 'b'));")
    [ -n "$COLS" ] || { echo "ERROR: table $t missing from the backup or the device." >&2; exit 1; }
    SQL+=" DELETE FROM main.$t; INSERT INTO main.$t ($COLS) SELECT $COLS FROM b.$t;"
done
SQL+=" UPDATE main.sync_clock SET applying = 0; COMMIT; PRAGMA main.wal_checkpoint(TRUNCATE);"
sqlite3 "$APP_DB" "$SQL" >/dev/null
sqlite3 "$APP_DB" "PRAGMA integrity_check;" | grep -qx ok || { echo "ERROR: merged xtream_v2.db failed integrity_check." >&2; exit 1; }

# Assemble what goes back: prefs and providers.db from the backup, the merged xtream_v2.db.
OUTGOING="$STAGE/out"
mkdir -p "$OUTGOING/databases"
cp -a "$STAGE/backup/shared_prefs" "$OUTGOING/"
cp -a "$STAGE/backup/databases/"providers.db* "$OUTGOING/databases/"
cp -a "$APP_DB" "$OUTGOING/databases/xtream_v2.db"

# Stale -wal/-shm left beside a replaced database would be replayed onto it, so they go first.
adb -s "$SERIAL" shell "run-as $PKG rm -f \
    $DATA/databases/providers.db-wal $DATA/databases/providers.db-shm \
    $DATA/databases/xtream_v2.db-wal $DATA/databases/xtream_v2.db-shm"
# `adb shell` stdin is binary-safe with the shell v2 protocol (checked by md5); `exec-in` silently did nothing.
tar -c -C "$OUTGOING" shared_prefs databases | adb -s "$SERIAL" shell "run-as $PKG tar -x -C $DATA"

echo "Restored $BACKUP to $SERIAL. Open the app to check."
