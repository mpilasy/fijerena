#!/usr/bin/env python3
"""Gate: in a Kotlin class, every property with a backing field comes before the first init block.

Kotlin sets properties and runs init blocks in the order they are written. An init block that
starts a coroutine (viewModelScope.launch on Dispatchers.Main.immediate, a Flow collected at once)
can read a property declared below it before it is set: a NullPointerException that depends on
timing. ProviderViewModel's init read its source statuses that way, and CI failed where a local run
passed (2026-11-10).

The check reads each class body in main sources and fails on a property with an initializer or
a delegate (`= ...`, `by ...`) below an init block of the same class. A property with only a
getter has no backing field and may stay anywhere, and so may an init block that only calls
require or check. Move the property, or the init block, so that
init comes last.

Run locally: scripts/check-init-order.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ["core", "tv", "mobile"]

PROPERTY = re.compile(
    r"^(?:(?:private|protected|internal|public|override|open|final|lateinit|const|abstract)\s+)*"
    r"(?:val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\b(?P<rest>.*)$"
)
INIT = re.compile(r"^init\s*\{")
CHECK_ONLY = re.compile(r"^(\}|(require|check|requireNotNull|checkNotNull)\s*\()")
# The last of these before a "{" tells what the brace opens.
KEYWORD = re.compile(
    r"::class|\bfun\b(?:\s+\w+)?|\b(?:class|object|interface|init|val|var|get|set|if|else|when|for|while|try|catch|finally|do|return)\b"
    r"|->|(?<![=!<>])=(?!=)"
)


def has_backing_field(rest: str) -> bool:
    """True when the text after the property name starts an initializer or a delegate."""
    head = rest.split("//")[0]
    for marker in ("get()", "set("):
        if marker in head:
            head = head[: head.index(marker)]
    return bool(re.search(r"(^|[^=!<>])=($|[^=])", head)) or bool(re.search(r"\bby\s", head))


def code_lines(text: str):
    """Yields (line number, code text, brace stack at line start, paren depth at line start).

    Strings, characters and comments are blanked, so their braces do not count. Each stack entry
    is "class", "init" or "block".
    """
    stack: list[dict] = []
    paren = 0
    i = 0
    n = len(text)
    line_no = 1
    line_start_stack = list(stack)
    line_start_paren = 0
    current = []
    statement = []

    def flush():
        nonlocal current, line_start_stack, line_start_paren
        yield_value = (line_no, "".join(current), line_start_stack, line_start_paren)
        current = []
        return yield_value

    out = []
    while i < n:
        c = text[i]
        if c == "\n":
            out.append(flush())
            line_no += 1
            line_start_stack = list(stack)
            line_start_paren = paren
            statement.append(" ")
            i += 1
            continue
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            depth = 1
            i += 2
            while i < n and depth:
                if text.startswith("/*", i):
                    depth += 1
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    if text[i] == "\n":
                        out.append(flush())
                        line_no += 1
                        line_start_stack = list(stack)
                        line_start_paren = paren
                    i += 1
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end < 0 else end + 3
            while end < n and text[end] == '"':
                end += 1
            for ch in text[i:end]:
                if ch == "\n":
                    out.append(flush())
                    line_no += 1
                    line_start_stack = list(stack)
                    line_start_paren = paren
            current.append('""')
            statement.append('""')
            i = end
            continue
        if c == '"' or c == "'":
            j = i + 1
            while j < n and text[j] != c and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            current.append(c * 2)
            statement.append(c * 2)
            i = j + 1
            continue
        if c == "{":
            keywords = KEYWORD.findall("".join(statement))
            last = keywords[-1] if keywords else ""
            kind = "init" if last == "init" else "class" if last in ("class", "object", "interface") else "block"
            stack.append({"kind": kind, "init": False})
            statement = []
        elif c == "}":
            if stack:
                stack.pop()
            statement = []
        elif c == "(":
            paren += 1
        elif c == ")":
            paren = max(0, paren - 1)
        elif c == ";":
            statement = []
        elif not paren:
            # What is in parentheses (a constructor's "val" parameters) does not name the block.
            statement.append(c)
        current.append(c)
        i += 1
    out.append(flush())
    return out


def check(path: Path) -> list[str]:
    problems = []
    text = path.read_text(encoding="utf-8")
    for line_no, code, stack, paren in code_lines(text):
        stripped = code.strip()
        # An init block that only checks its arguments starts no work: it may come first.
        if len(stack) > 1 and stack[-1]["kind"] == "init" and stack[-2]["kind"] == "class" and not paren:
            if stripped and not CHECK_ONLY.match(stripped):
                stack[-2]["init"] = True
            continue
        if not stack or stack[-1]["kind"] != "class" or paren:
            continue
        body = stack[-1]
        if INIT.match(stripped):
            rest = stripped[stripped.index("{") + 1 :].strip()
            if rest and not CHECK_ONLY.match(rest):
                body["init"] = True
            continue
        if not body["init"]:
            continue
        match = PROPERTY.match(stripped)
        if match and has_backing_field(match.group("rest")):
            rel = path.relative_to(ROOT)
            problems.append(f"{rel}:{line_no}: property '{match.group(1)}' is declared after an init block.")
    return problems


def main() -> int:
    problems = []
    for top in SOURCES:
        for path in sorted((ROOT / top).rglob("src/main/**/*.kt")):
            if "/build/" in str(path):
                continue
            problems += check(path)
    if problems:
        print("\n".join(problems))
        print(f"\n{len(problems)} problem(s). Declare these properties before the class's first init block.")
        return 1
    print("Init order gate: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
