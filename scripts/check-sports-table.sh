#!/bin/bash
# Gate for the sports teams plan → The season table.
#
# Checks core/network/src/main/assets/sports_broadcasters.json, the broadcasters of each
# competition per country and season (read by SeasonTable):
#   - the file is valid JSON with version, updated, regions, competitions and rights;
#   - competition keys are unique, and each competition has a name, a sport and its sources;
#   - every rights row names a known competition, has broadcasters, and its from and to are ISO
#     dates with from <= to;
#   - every country is an ISO 3166-1 alpha-2 code (upper case), a region key, or WORLD.
# Fails on invalid data. Warns (does not fail) for each rights row whose `to` date has passed:
# update the table for the new season.
#
# Run locally: scripts/check-sports-table.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TABLE="$ROOT_DIR/core/network/src/main/assets/sports_broadcasters.json"

python3 - "$TABLE" <<'PY'
import datetime
import json
import sys

# ISO 3166-1 alpha-2 codes, plus XK (Kosovo, used by UEFA and others).
ISO = set("""
AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS
BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE
EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM
HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC
LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA
NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW
SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO
TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW XK
""".split())
WORLD = "WORLD"

path = sys.argv[1]
errors = []
warnings = []


def date(value, where):
    try:
        return datetime.date.fromisoformat(value)
    except (TypeError, ValueError):
        errors.append(f"{where}: not an ISO date: {value!r}")
        return None


try:
    with open(path, encoding="utf-8") as f:
        table = json.load(f)
except (OSError, ValueError) as e:
    print(f"{path}: cannot read the table: {e}")
    sys.exit(1)

for field in ("version", "updated", "competitions", "rights"):
    if field not in table:
        errors.append(f"missing field: {field}")
if not isinstance(table.get("version"), int):
    errors.append("version is not a number")
date(table.get("updated"), "updated")

regions = table.get("regions", {})
for name, codes in regions.items():
    if len(name) <= 2 or name == WORLD:
        errors.append(f"region {name}: a region key must be longer than 2 letters and not {WORLD}")
    for code in codes:
        if code not in ISO:
            errors.append(f"region {name}: not an ISO country code: {code!r}")

keys = set()
for i, competition in enumerate(table.get("competitions", [])):
    key = competition.get("key")
    where = f"competition {key or i}"
    if not key:
        errors.append(f"{where}: no key")
    elif key in keys:
        errors.append(f"{where}: key used twice")
    keys.add(key)
    for field in ("name", "sport"):
        if not competition.get(field):
            errors.append(f"{where}: no {field}")
    if not competition.get("sources"):
        errors.append(f"{where}: no sources")

today = datetime.date.today()
for i, rights in enumerate(table.get("rights", [])):
    where = f"rights row {i + 1} ({rights.get('competition')}, {rights.get('season')})"
    if rights.get("competition") not in keys:
        errors.append(f"{where}: unknown competition")
    if not rights.get("season"):
        errors.append(f"{where}: no season")
    countries = rights.get("countries") or []
    if not countries:
        errors.append(f"{where}: no countries")
    for country in countries:
        if country != WORLD and country not in regions and country not in ISO:
            errors.append(f"{where}: not an ISO country code, a region or {WORLD}: {country!r}")
    broadcasters = rights.get("broadcasters") or []
    if not broadcasters or not all(isinstance(b, str) and b.strip() for b in broadcasters):
        errors.append(f"{where}: no broadcasters, or a blank one")
    start = date(rights.get("from"), where)
    end = date(rights.get("to"), where)
    if start and end:
        if start > end:
            errors.append(f"{where}: from {start} is after to {end}")
        elif end < today:
            warnings.append(f"{where}: ended on {end} ({' '.join(countries)})")

for warning in warnings:
    print(f"warning: {warning}")
if errors:
    for error in errors:
        print(f"error: {error}")
    print()
    print(f"The season table {path} is not valid. See the sports teams plan → The season table.")
    sys.exit(1)
rows = len(table.get("rights", []))
print(f"Sports season table: OK ({len(keys)} competitions, {rows} rights rows, {len(warnings)} ended)")
PY
