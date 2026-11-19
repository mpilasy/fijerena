#!/bin/bash
# Gate for CONTRIBUTING.md → "Commit messages": the first line of each commit is a
# Conventional Commit, `<type>(<scope>): <description>`, 72 characters or fewer, with a
# description that starts with a lowercase letter and has no full stop at the end.
#
# Checks the commits in the range given as $1 (default: origin/main..HEAD). Merge commits and
# commits from before the rule (2d5824c1f is the first commit under it) are not checked.
#
# Run locally: scripts/check-commit-messages.sh [<range>]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FIRST_CHECKED="2d5824c1f"
TYPES="feat|fix|perf|refactor|test|docs|build|ci|chore|revert"
PATTERN="^($TYPES)(\([a-z0-9-]+\))?!?: [a-z0-9\`].*[^.]$"
RANGE="${1:-origin/main..HEAD}"

failed=0
while read -r sha; do
  subject="$(git log -1 --format=%s "$sha")"
  problem=""
  if ! grep -qE "$PATTERN" <<<"$subject"; then
    problem="not '<type>(<scope>): <description>' (types: ${TYPES//|/, }; lowercase start, no full stop)"
  elif ((${#subject} > 72)); then
    problem="first line is ${#subject} characters, more than 72"
  fi
  if [[ -n "$problem" ]]; then
    echo "${sha:0:9} \"$subject\": $problem"
    failed=1
  fi
done < <(git rev-list --no-merges "$RANGE" --not "$FIRST_CHECKED^")

if ((failed)); then
  echo "Write commit messages as CONTRIBUTING.md → \"Commit messages\" says."
  exit 1
fi
echo "Commit messages: OK"
