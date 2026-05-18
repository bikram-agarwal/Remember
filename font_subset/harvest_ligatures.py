"""
Walk app/src/main/java and extract every Material Symbols ligature name we use.

Sources of truth (any one match is enough to keep the glyph):
- `*MaterialRoundedSymbol(name = "xxx", ...)` literal calls.
- `iconName = "xxx"` / `leadingIconName = "xxx"` wrapper parameters.
- `materialSymbolName = "xxx"` / `leadingMaterialSymbolName = "xxx"` wrapper parameters.
- `symbolName = "xxx"` field assignments.
- String literals returned from `*.materialSymbolName()`-style helper functions.
- String literals returned from file-extension `when` branches such as
  `"mp3", "wav" -> "audio_file"` and `else -> "draft"`.

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
    ("MaterialRoundedSymbol", re.compile(rf'\b[A-Za-z0-9_]*MaterialRoundedSymbol\s*\(\s*(?:name\s*=\s*)?{LIGATURE}', re.MULTILINE)),
    # Do not use [^)]* here: modifier args often contain `)` (e.g. Modifier.size(24.dp)).
    ("MaterialRoundedSymbol_multiline", re.compile(
        rf'\b[A-Za-z0-9_]*MaterialRoundedSymbol\s*\(\s*.*?\bname\s*=\s*{LIGATURE}',
        re.DOTALL,
    )),
    ("materialSymbolName_assign", re.compile(rf'materialSymbolName\s*=\s*{LIGATURE}')),
    ("materialSymbolName_param", re.compile(rf'materialSymbolName\s*:\s*String\??\s*=\s*{LIGATURE}')),
    ("iconName_assign", re.compile(rf'\biconName\s*=\s*{LIGATURE}')),
    ("iconName_param", re.compile(rf'\biconName\s*:\s*String\??\s*=\s*{LIGATURE}')),
    ("leadingIconName_assign", re.compile(rf'\bleadingIconName\s*=\s*{LIGATURE}')),
    ("leadingIconName_param", re.compile(rf'\bleadingIconName\s*:\s*String\??\s*=\s*{LIGATURE}')),
    ("leadingMaterialSymbolName", re.compile(rf'leadingMaterialSymbolName\s*=\s*{LIGATURE}')),
    ("symbolName_field", re.compile(rf'symbolName\s*=\s*{LIGATURE}')),
    # `name = if (cond) "icon_a" else "icon_b"` or wrapper variants like
    # `iconName = if (...) "restore_from_trash" else "delete_forever"`.
    # The simpler `name = "x"` patterns stop at the first `)` inside the condition.
    ("symbol_name_if_else", re.compile(
        r'\b(?:name|iconName|leadingIconName|materialSymbolName|leadingMaterialSymbolName|symbolName)\s*=\s*if\s*\([^)]*\)\s*(?:\{\s*)?"([a-z][a-z0-9_]+)"\s*(?:\}\s*)?else\s*(?:\{\s*)?"([a-z][a-z0-9_]+)"',
        re.DOTALL,
    )),
    # `EnumType.FOO -> "icon"` in `fun EnumType.materialSymbolName()`-style maps.
    ("enum_icon_arrow", re.compile(
        r'\b[A-Z][A-Za-z0-9_]*\.\w+\s*->\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
    # Kotlin when branches that map string literals to icon ligatures, e.g.
    # `"mp3", "wav" -> "audio_file"` in file type helpers.
    ("string_when_icon_arrow", re.compile(
        r'(?:"[^"]+"\s*,\s*)*"[^"]+"\s*->\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
    # Fallback branches in icon-name helper functions, e.g. `else -> "draft"`.
    ("else_icon_arrow", re.compile(
        r'\belse\s*->\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
    # Enum constructor entries like `ARCHIVE("archive")`.
    ("enum_entry_first_string_arg", re.compile(
        r'^\s*[A-Z][A-Z0-9_]*\(\s*"([a-z][a-z0-9_]+)"\s*\)\s*,?',
        re.MULTILINE,
    )),
    # Enum constructor entries like `Notes(R.string.main_tab_notes, "notes")`.
    ("enum_entry_second_string_arg", re.compile(
        r'^\s*[A-Z][A-Za-z0-9_]*\(\s*R\.string\.\w+\s*,\s*"([a-z][a-z0-9_]+)"',
        re.MULTILINE,
    )),
]

ENABLED_FILES_HINT = {
    "MaterialRoundedSymbol",
    "iconName",
    "leadingIconName",
    "materialSymbolName",
    "leadingMaterialSymbolName",
    "symbolName",
}

EXCLUDE_DIRS = {"build", "generated", ".gradle"}


def file_is_relevant(text: str) -> bool:
    return any(token in text for token in ENABLED_FILES_HINT)


def harvest() -> dict[str, set[str]]:
    by_pattern: dict[str, set[str]] = {name: set() for name, _ in PATTERNS}
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
