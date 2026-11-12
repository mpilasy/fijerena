#!/bin/bash
# Gate for the provider data tests plan. This repository is public, and the test fixtures hold
# provider answers (recorded `rec-` files and hand-written `var-` files). A login, a provider's
# host or a token in a fixture is a leak.
#
# Fails on a fixture line that has:
#   - a URL with a user and password in it, or a host not on the allow list: `*.invalid`,
#     `example.com` / `.org` / `.net`, `localhost`, `127.0.0.1`, `10.0.2.2`, the documentation
#     address ranges below, and under fixtures/sports/ the public sports hosts;
#   - an IPv4 address other than 127.0.0.1, 10.0.2.2 and the documentation ranges (192.0.2.0/24,
#     198.51.100.0/24, 203.0.113.0/24). A version after a product name ("Chrome/120.0.0.0") is
#     not an address;
#   - `username=` or `password=` with a value other than `user` / `pass`, or `api_key=`, `apikey=`,
#     `token=` with any value (also as JSON fields: `"username": "…"`);
#   - an Authorization header.
#
# Run locally: scripts/check-fixture-privacy.sh [file or folder...]
# With no argument it checks every core/*/src/test/resources/fixtures folder.
set -euo pipefail

if [ $# -eq 0 ]; then
    cd "$(dirname "${BASH_SOURCE[0]}")/.."
    set -- core/*/src/test/resources/fixtures
fi

files=()
while IFS= read -r -d '' f; do files+=("$f"); done < <(find "$@" -type f -print0 | sort -z)
if [ ${#files[@]} -eq 0 ]; then
    echo "Fixture privacy gate: OK (no fixtures)"
    exit 0
fi

GLOBAL_HOSTS="invalid example.com example.org example.net localhost 127.0.0.1 10.0.2.2"
SPORTS_HOSTS="espn.com espncdn.com espn.co.uk espn.in espn.pvt mlb.com nhle.com nhl.com
d2igy0yla8zi0u.cloudfront.net ticketmaster.com vividseats.com stlouisunionstation.com"

# True when $1 is one of the domains in $2 or a subdomain of one.
on_list() {
    local host="$1" domain
    for domain in $2; do
        if [ "$host" = "$domain" ] || [[ "$host" == *".$domain" ]]; then
            return 0
        fi
    done
    return 1
}

findings=()

# URLs: user and password in the URL, and the host.
while IFS= read -r hit; do
    rest="${hit#*:}"
    where="${hit%%:*}:${rest%%:*}"
    url="${rest#*:}"
    authority="${url#*://}"
    host="${authority##*@}"
    host="${host%%:*}"
    host="${host,,}"
    allowed="$GLOBAL_HOSTS"
    if [[ "$where" == *fixtures/sports/* ]]; then
        allowed="$allowed $SPORTS_HOSTS"
    fi
    if [[ "$authority" == *@* ]]; then
        findings+=("$where: a URL with a login in it ($url)")
    elif ! on_list "$host" "$allowed" && [[ ! "$host" =~ ^(192\.0\.2|198\.51\.100|203\.0\.113)\.[0-9]+$ ]]; then
        findings+=("$where: a URL host not on the allow list ($host)")
    fi
done < <(grep -HnoiE 'https?://[^/"'\''<>[:space:]?#\\]+' "${files[@]}" || true)

# IPv4 addresses.
while IFS= read -r hit; do
    where="$(printf '%s' "$hit" | cut -d: -f1-2)"
    ip="$(printf '%s' "$hit" | cut -d: -f3- | grep -oE '([0-9]{1,3}\.){3}[0-9]{1,3}')"
    case "$ip" in
        127.0.0.1 | 10.0.2.2 | 192.0.2.* | 198.51.100.* | 203.0.113.*) ;;
        *) findings+=("$where: an IPv4 address ($ip)") ;;
    esac
done < <(grep -HnoE '(^|[^0-9.A-Za-z/]|[^A-Za-z]/)([0-9]{1,3}\.){3}[0-9]{1,3}($|[^0-9.])' "${files[@]}" || true)

# Logins and tokens, as query parameters and as JSON fields.
while IFS= read -r hit; do
    where="$(printf '%s' "$hit" | cut -d: -f1-2)"
    pair="$(printf '%s' "$hit" | cut -d: -f3-)"
    name="$(printf '%s' "$pair" | grep -oiE '^"?[a-z_-]+' | tr -d '"' | tr '[:upper:]' '[:lower:]')"
    value="$(printf '%s' "$pair" | sed -E 's/^"?[A-Za-z_-]+"?[[:space:]]*[=:][[:space:]]*"?//; s/"$//')"
    case "$name:$value" in
        username:user | username: | password:pass | password: | *api_key: | apikey: | *token:) ;;
        *) findings+=("$where: a login or token value ($name)") ;;
    esac
done < <(grep -HnoiE '(username|password|[a-z_]*api_key|apikey|[a-z_-]*token)=[^&"'\''[:space:]]*|"(username|password|[a-z_]*api_key|apikey|[a-z_-]*token)"[[:space:]]*:[[:space:]]*"[^"]*"' "${files[@]}" || true)

# Authorization headers.
while IFS= read -r hit; do
    findings+=("$(printf '%s' "$hit" | cut -d: -f1-2): an Authorization header")
done < <(grep -HnoiE '"?authorization"?[[:space:]]*[:=]' "${files[@]}" || true)

if [ ${#findings[@]} -gt 0 ]; then
    printf '%s\n' "${findings[@]}"
    echo
    echo "A fixture must hold no login, provider host, address or token: this repository is public."
    echo "Clean it as scripts/record-provider-fixtures.py does (user, pass, *.invalid hosts)."
    exit 1
fi
echo "Fixture privacy gate: OK (${#files[@]} files)"
