"""
Build app/src/main/res/raw/icon_keywords.json from Google's Material Symbols
metadata API, filtered to the icons that appear in BundledMaterialSymbolIcons.kt.

Each icon Google ships has an associated `categories` (e.g. "action", "places")
and `tags` list (synonyms / search hints like "business", "office", "job").
Adding these to the picker's search corpus turns concept queries like "yoga",
"workout", "passport" into real hits even when the icon name is something
unobvious like `self_improvement` or `fitness_center`.

Output JSON shape (compact, keyed by ligature name):

    {
      "self_improvement": ["calm", "meditate", "mindfulness", "yoga", "zen"],
      "fitness_center":   ["dumbell", "exercise", "gym", "weight", "workout"],
      ...
    }

Run: `python font_subset/build_search_keywords.py` (no extra deps; stdlib only).
Pass `--force-refresh` to bypass the cache and re-download.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

# User-replaceable defaults.
METADATA_URL_DEFAULT = "https://fonts.google.com/metadata/icons?incomplete=true&key=material_symbols"
OUTPUT_PATH_DEFAULT = "app/src/main/res/raw/icon_keywords.json"

# Internal constants.
REPO = Path(__file__).resolve().parents[1]
CACHE_DIR = Path(__file__).with_name("cache")
CATALOG_FILE = REPO / "app/src/main/java/dev/bikram/remember/ui/edit/BundledMaterialSymbolIcons.kt"

# Google prepends this XSSI guard to its JSON metadata responses.
JSON_XSSI_PREFIX = ")]}'"

# Match `symbolName = "lowercase_with_underscores"` rows in the catalog.
SYMBOL_NAME_PATTERN = re.compile(r'symbolName\s*=\s*"([a-z][a-z0-9_]+)"')


def fetch(url: str, cache_path: Path, *, force_refresh: bool) -> str:
    if cache_path.exists() and not force_refresh:
        return cache_path.read_text(encoding="utf-8")
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"  downloading {url}", file=sys.stderr)
    request = urllib.request.Request(url, headers={"User-Agent": "remember/build-icon-keywords"})
    with urllib.request.urlopen(request, timeout=60) as response:
        text = response.read().decode("utf-8")
    cache_path.write_text(text, encoding="utf-8")
    return text


def load_catalog_ligatures() -> set[str]:
    if not CATALOG_FILE.exists():
        raise FileNotFoundError(f"Could not find catalog file: {CATALOG_FILE}")
    text = CATALOG_FILE.read_text(encoding="utf-8")
    ligatures = set(SYMBOL_NAME_PATTERN.findall(text))
    if not ligatures:
        raise RuntimeError("No `symbolName = \"...\"` rows found in the catalog file")
    return ligatures


def parse_metadata(raw: str) -> list[dict[str, object]]:
    body = raw.strip()
    if body.startswith(JSON_XSSI_PREFIX):
        body = body[len(JSON_XSSI_PREFIX) :]
    payload = json.loads(body)
    icons = payload.get("icons")
    if not isinstance(icons, list):
        raise RuntimeError("Unexpected metadata shape: missing `icons` array")
    return icons


def build_keyword_map(
    icons: list[dict[str, object]],
    catalog: set[str],
) -> dict[str, list[str]]:
    keyword_map: dict[str, list[str]] = {}
    for icon in icons:
        name = icon.get("name")
        if not isinstance(name, str) or name not in catalog:
            continue
        categories = icon.get("categories") or []
        tags = icon.get("tags") or []
        # Tag list quality is uneven upstream; drop tokens that just echo the name.
        name_tokens = set(name.split("_"))
        keywords: list[str] = []
        for raw in [*categories, *tags]:
            if not isinstance(raw, str):
                continue
            cleaned = raw.strip().lower()
            if not cleaned or cleaned in name_tokens:
                continue
            if cleaned not in keywords:
                keywords.append(cleaned)
        if keywords:
            keyword_map[name] = keywords
    return keyword_map


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--metadata-url", default=METADATA_URL_DEFAULT, help="Material Symbols metadata endpoint")
    parser.add_argument("--output", default=OUTPUT_PATH_DEFAULT, help="Path (relative to repo root) for the emitted JSON")
    parser.add_argument("--force-refresh", action="store_true", help="Bypass cached downloads")
    args = parser.parse_args(argv)

    catalog = load_catalog_ligatures()
    print(f"catalog ligatures: {len(catalog)}", file=sys.stderr)

    metadata_text = fetch(
        args.metadata_url,
        CACHE_DIR / "material-symbols-metadata.json",
        force_refresh=args.force_refresh,
    )
    icons = parse_metadata(metadata_text)
    keyword_map = build_keyword_map(icons, catalog)

    missing = sorted(catalog - keyword_map.keys())
    print(f"  enriched: {len(keyword_map)}, missing tags upstream: {len(missing)}", file=sys.stderr)
    if missing:
        print("  (no upstream tags for: " + ", ".join(missing[:8]) + (" ..." if len(missing) > 8 else "") + ")", file=sys.stderr)

    # Emit a stable, sorted JSON to keep diffs minimal across runs.
    sorted_map = {name: keyword_map[name] for name in sorted(keyword_map)}
    output_path = REPO / args.output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(sorted_map, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"  wrote {output_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
