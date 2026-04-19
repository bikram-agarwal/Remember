"""
Walk app/src/main/java and extract every Material Symbols ligature name we use.

Sources of truth (any one match is enough to keep the glyph):
- `RememberMaterialRoundedSymbol(name = "xxx", ...)` literal calls.
- `materialSymbolName = "xxx"` (data class / enum property assignments).
- `symbolName = "xxx"` (IconChoice entries in BundledMaterialSymbolIcons.kt).
- String literals returned from `ActionType.materialSymbolName()`-style helper functions.

We err on the side of including extras: ligatures are cheap (~50-200 bytes
each), but a missing one ships as an invisible icon in production.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
JAVA_ROOT = REPO / "app" / "src" / "main" / "java"
OUTPUT_LIST = Path(__file__).with_name("ligatures.txt")
OUTPUT_REPORT = Path(__file__).with_name("ligatures_report.json")

LIGATURE = r'"([a-z][a-z0-9_]+)"'

PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("RememberMaterialRoundedSymbol", re.compile(rf'RememberMaterialRoundedSymbol\s*\(\s*(?:name\s*=\s*)?{LIGATURE}', re.MULTILINE)),
    # Do not use [^)]* here: modifier args often contain `)` (e.g. Modifier.size(24.dp)).
    ("RememberMaterialRoundedSymbol_multiline", re.compile(
        rf'RememberMaterialRoundedSymbol\s*\(\s*.*?\bname\s*=\s*{LIGATURE}',
        re.DOTALL,
    )),
    ("materialSymbolName_assign", re.compile(rf'materialSymbolName\s*=\s*{LIGATURE}')),
    ("materialSymbolName_param", re.compile(rf'materialSymbolName\s*:\s*String\??\s*=\s*{LIGATURE}')),
    ("leadingMaterialSymbolName", re.compile(rf'leadingMaterialSymbolName\s*=\s*{LIGATURE}')),
    ("symbolName_field", re.compile(rf'symbolName\s*=\s*{LIGATURE}')),
    # MainTab entries use (label, symbolName) pairs, not `symbolName =`:
    #   Notes("Notes", "notes"),
    ("MainTab_enum_symbol", re.compile(
        rf'\b(?:Notes|History|Settings)\(\s*"[^"]*",\s*{LIGATURE}\s*\)',
    )),
    # `name = if (cond) "icon_a" else "icon_b"` on RememberMaterialRoundedSymbol rows
    # (the simpler `name = "x"` patterns stop at the first `)` inside the condition).
    ("material_symbol_name_if_else", re.compile(
        r'\bname\s*=\s*if\s*\([^)]*\)\s*"([a-z][a-z0-9_]+)"\s*else\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
    # `ActionType.FOO -> "icon"` in `fun ActionType.materialSymbolName()`-style maps.
    ("action_type_icon_arrow", re.compile(
        r'\bActionType\.\w+\s*->\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
]

ENABLED_FILES_HINT = {
    "RememberMaterialRoundedSymbol",
    "materialSymbolName",
    "leadingMaterialSymbolName",
    "symbolName",
    "FilledRoundedSymbol",
}

EXCLUDE_DIRS = {"build", "generated", ".gradle"}


def file_is_relevant(text: str) -> bool:
    return any(token in text for token in ENABLED_FILES_HINT)


def harvest() -> dict[str, set[str]]:
    by_pattern: dict[str, set[str]] = {name: set() for name, _ in PATTERNS}
    by_pattern["NoteSwipeAction_enum"] = set()
    swipe_icon = re.compile(r'^[A-Z_]+\("([a-z][a-z0-9_]+)"\)\s*,', re.MULTILINE)
    for path in JAVA_ROOT.rglob("*.kt"):
        if any(part in EXCLUDE_DIRS for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        if not file_is_relevant(text):
            continue
        for name, pattern in PATTERNS:
            for match in pattern.finditer(text):
                groups = match.groups()
                for ligature in groups:
                    if ligature is None:
                        continue
                    # Filter obvious non-ligatures.
                    if ligature in {"name", "symbolName", "materialSymbolName"}:
                        continue
                    if ligature.startswith("ic_") or ligature.startswith("symbol:"):
                        continue
                    by_pattern[name].add(ligature)
        if path.name == "NoteSwipeAction.kt":
            for match in swipe_icon.finditer(text):
                by_pattern["NoteSwipeAction_enum"].add(match.group(1))
    return by_pattern


def main() -> int:
    by_pattern = harvest()
    union: set[str] = set().union(*by_pattern.values())
    union.discard("")

    OUTPUT_LIST.write_text("\n".join(sorted(union)) + "\n", encoding="utf-8")

    report = {name: sorted(values) for name, values in by_pattern.items()}
    report["_union_count"] = len(union)
    OUTPUT_REPORT.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")

    print(f"Collected {len(union)} unique Material Symbols ligatures")
    print(f"  -> {OUTPUT_LIST}")
    print(f"  -> {OUTPUT_REPORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
