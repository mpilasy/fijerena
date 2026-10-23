#!/usr/bin/env python3
"""Part of scripts/check-design-system.sh: text inside a button follows the button's colour.

A button's label must take the button's own content colour (CinemaText's default inside a
button, TextTone.Inherit): a fixed tone (Primary, Secondary, ...) stays light when a TV button's
ground turns light on focus, and the label vanishes (the PIN pad's focused key, 2026-10-18).
Fails on a CinemaText with a tone other than Inherit inside the content of any *Button( or
*Key( call. A line marked `// look-ok: <reason>` (or the line above) is let through.
"""
import pathlib
import re
import sys

CALL = re.compile(r"\b\w*(?:Button|Key)\(")
FIXED_TONE = re.compile(r"tone\s*=\s*TextTone\.(?!Inherit\b)\w+")


def matching(s, start, open_c, close_c):
    depth = 0
    for k in range(start, len(s)):
        if s[k] == open_c:
            depth += 1
        elif s[k] == close_c:
            depth -= 1
            if depth == 0:
                return k
    return len(s) - 1


def problems(path):
    s = path.read_text()
    lines = s.split("\n")
    found = set()
    for m in CALL.finditer(s):
        if s[: m.start()].rstrip().endswith("fun"):
            continue
        end = matching(s, m.end() - 1, "(", ")")
        after = s[end + 1 :].lstrip()
        if not after.startswith("{"):
            continue
        body_start = s.index("{", end)
        body_end = matching(s, body_start, "{", "}")
        body = s[body_start:body_end]
        for t in re.finditer(r"CinemaText\(", body):
            at = body_start + t.start()
            call = s[at : matching(s, at + len("CinemaText"), "(", ")") + 1]
            if FIXED_TONE.search(call):
                line = s[:at].count("\n")
                marked = "look-ok" in lines[line] or (line > 0 and "look-ok" in lines[line - 1])
                if not marked:
                    found.add(line + 1)
    return sorted(found)


def main():
    failures = 0
    for root in sys.argv[1:]:
        for path in sorted(pathlib.Path(root).rglob("*.kt")):
            for line in problems(path):
                print(f"{path}:{line}: a button's text has a fixed tone — leave tone out (it follows the button) or use TextTone.Inherit.")
                failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
