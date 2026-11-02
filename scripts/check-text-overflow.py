#!/usr/bin/env python3
"""Part of scripts/check-design-system.sh: text cut to a number of lines ends with an ellipsis.

A text call (CinemaText, TvContentText, TvBackdropText; CinemaCodeText always ellipsizes) that
sets `maxLines` must also set `overflow`: the components clip by default, and a title cut in the middle of a letter
without "…" read as a bug (Live TV preview, cards; consistency round 2, finding 38). Give
`overflow = TextOverflow.Ellipsis`, or `TextOverflow.Clip` where the text scrolls (a marquee). A
call with `maxLines = Int.MAX_VALUE` is not cut. A line marked `// look-ok: <reason>` (or the line
above) is let through.

Usage: check-text-overflow.py DIR...   (exit 1 and one line per problem)
"""
import pathlib
import re
import sys

CALL = re.compile(r"(?<![A-Za-z0-9_.])(CinemaText|TvContentText|TvBackdropText)\(")


def matching(s, start):
    depth = 0
    in_str = False
    k = start
    while k < len(s):
        c = s[k]
        if in_str:
            if c == "\\":
                k += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return k
        k += 1
    return len(s) - 1


def problems(path):
    s = path.read_text()
    lines = s.split("\n")
    found = []
    for m in CALL.finditer(s):
        line_no = s.count("\n", 0, m.start())
        line = lines[line_no]
        if line.lstrip().startswith(("//", "*", "/*")) or s[: m.start()].rstrip().endswith("fun"):
            continue
        if "look-ok:" in line or (line_no > 0 and "// look-ok:" in lines[line_no - 1]):
            continue
        end = matching(s, m.end() - 1)
        call = s[m.end() : end]
        if re.search(r"(?<![A-Za-z0-9_])maxLines\s*=(?!\s*Int\.MAX_VALUE)", call) and not re.search(
            r"(?<![A-Za-z0-9_])overflow\s*=", call
        ):
            found.append(f"{path}:{line_no + 1}")
    return found


def main(dirs):
    bad = []
    for d in dirs:
        for p in sorted(pathlib.Path(d).rglob("*.kt")):
            bad += problems(p)
    for b in bad:
        print(f"{b}: maxLines without overflow — add overflow = TextOverflow.Ellipsis (or Clip under a marquee).")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
