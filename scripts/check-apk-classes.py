#!/usr/bin/env python3
"""Check that a built APK holds every class its modules compiled, before it goes on a device.

A second Gradle build in the same project (an editor's Kotlin language server importing the
project, a second terminal) can rewrite a module's class folder while this build dexes it. The
dex output is then short of classes, the build cache keeps it, and the APK crashes at start with
NoClassDefFoundError: on 2026-11-11 the localRelease APKs lost core:player's AppScopes that way.

The check lists the classes that the app module and each core library compiled for the variant
(localRelease and release use the libraries' release output, debug their debug output), reads the
class names in the APK's dex files, and fails when one is missing.

Usage: scripts/check-apk-classes.py <tv|mobile> <variant> [apk]
       (default apk: <module>/build/outputs/apk/<variant>/<module>-<variant>.apk)
"""

import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def class_dirs(module: str, variant: str) -> list[Path]:
    lib_variant = "debug" if variant == "debug" else "release"
    dirs = [ROOT / module / "build/intermediates/built_in_kotlinc" / variant / f"compile{variant[0].upper()}{variant[1:]}Kotlin/classes"]
    lib_task = f"compile{lib_variant.capitalize()}Kotlin"
    dirs += sorted(ROOT.glob(f"core/*/build/intermediates/built_in_kotlinc/{lib_variant}/{lib_task}/classes"))
    return dirs


def compiled_classes(dirs: list[Path]) -> set[str]:
    names = set()
    for d in dirs:
        for f in d.rglob("*.class"):
            rel = f.relative_to(d).as_posix()
            if rel.startswith("META-INF/") or rel.endswith("module-info.class"):
                continue
            names.add(f"L{rel[: -len('.class')]};")
    return names


def uleb128(data: bytes, pos: int) -> tuple[int, int]:
    result, shift = 0, 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, pos
        shift += 7


def dex_classes(dex: bytes) -> set[str]:
    """The descriptors of the classes a dex file defines (class_defs → type_ids → string_ids)."""
    string_ids_off = struct.unpack_from("<I", dex, 0x3C)[0]
    type_ids_off = struct.unpack_from("<I", dex, 0x44)[0]
    class_defs_size, class_defs_off = struct.unpack_from("<II", dex, 0x60)
    names = set()
    for i in range(class_defs_size):
        type_idx = struct.unpack_from("<I", dex, class_defs_off + i * 32)[0]
        string_idx = struct.unpack_from("<I", dex, type_ids_off + type_idx * 4)[0]
        data_off = struct.unpack_from("<I", dex, string_ids_off + string_idx * 4)[0]
        _, start = uleb128(dex, data_off)
        end = dex.index(b"\0", start)
        names.add(dex[start:end].decode("utf-8", "replace"))
    return names


def main() -> int:
    if len(sys.argv) not in (3, 4):
        print(__doc__, file=sys.stderr)
        return 2
    module, variant = sys.argv[1], sys.argv[2]
    apk = Path(sys.argv[3]) if len(sys.argv) == 4 else ROOT / module / f"build/outputs/apk/{variant}/{module}-{variant}.apk"
    dirs = [d for d in class_dirs(module, variant) if d.is_dir()]
    if not dirs:
        print(f"check-apk-classes: no compiled classes found for {module} {variant}", file=sys.stderr)
        return 1
    expected = compiled_classes(dirs)
    with zipfile.ZipFile(apk) as z:
        found = set()
        for name in z.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                found |= dex_classes(z.read(name))
    missing = sorted(expected - found)
    if missing:
        print(f"check-apk-classes: {apk.name} is missing {len(missing)} of {len(expected)} compiled classes:", file=sys.stderr)
        for name in missing[:20]:
            print(f"  {name}", file=sys.stderr)
        print(
            "Do not install it. Stop any other Gradle build of this project (an editor importing it),\n"
            "then rebuild with: ./gradlew clean && ./gradlew --no-build-cache <task>",
            file=sys.stderr,
        )
        return 1
    print(f"check-apk-classes: {apk.name} holds all {len(expected)} compiled classes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
