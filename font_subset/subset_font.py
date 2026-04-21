"""
Take the Material Symbols Rounded variable TTF and produce a pair of small
static TTFs (one filled, one outlined) that contain only the ligatures the app
references.

Pipeline (runs twice, once per FILL value):
1. INSTANCE the variable font at our preferred axis values
   (FILL=0 or 1, wght=500, GRAD=0, opsz=24). This collapses the per-glyph
   variation table (gvar) - the bulk of the source 14 MB - into baked outlines.
2. SUBSET the static font: ligature_resolve.expand_wanted_icon_names maps each
   icon string (e.g. delete_outline) to the real LigGlyph output (e.g. delete)
   plus its component glyphs; pyftsubset is run with --no-layout-closure so we
   do not pull in every ligature that shares the Latin alphabet.

Outputs (both ~100-300 KB):
  material_symbols_rounded_subset.ttf           (FILL=1, filled)
  material_symbols_rounded_outlined_subset.ttf  (FILL=0, outlined)

Run AFTER `harvest_ligatures.py` so the ligature list is fresh.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

from fontTools.ttLib import TTFont

from ligature_resolve import expand_wanted_icon_names

HERE = Path(__file__).resolve().parent
LIGATURES_FILE = HERE / "ligatures.txt"
GLYPHS_EXPANDED_FILE = HERE / "glyphs_expanded.txt"

# Two-variant output: filled keeps the original name so callers that don't opt
# into the outlined variant stay on the same path, outlined gets its own file.
VARIANTS = [
    {
        "name": "filled",
        "fill": 1,
        "instanced": HERE / "material_symbols_rounded_instanced.ttf",
        "subset": HERE / "material_symbols_rounded_subset.ttf",
    },
    {
        "name": "outlined",
        "fill": 0,
        "instanced": HERE / "material_symbols_rounded_outlined_instanced.ttf",
        "subset": HERE / "material_symbols_rounded_outlined_subset.ttf",
    },
]

# Axes held constant across both variants. FILL is per-variant and injected
# below when assembling the instancer argv.
SHARED_AXES = {
    "wght": 500,
    "GRAD": 0,
    "opsz": 24,
}


def _source_ttf_candidates() -> list[Path]:
    candidates: list[Path] = []
    env_path = os.environ.get("MATERIAL_SYMBOLS_ROUNDED_TTF")
    if env_path:
        candidates.append(Path(env_path))
    candidates.append(HERE / "material_symbols_rounded.ttf")
    return candidates


SOURCE_TTF_CANDIDATES = _source_ttf_candidates()


def find_source_ttf() -> Path:
    for candidate in SOURCE_TTF_CANDIDATES:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "No source TTF found. Place material_symbols_rounded.ttf in font_subset/ "
        "or set MATERIAL_SYMBOLS_ROUNDED_TTF. Tried: "
        + ", ".join(str(path) for path in SOURCE_TTF_CANDIDATES)
    )


def run(args: list[str]) -> int:
    print("Running:", " ".join(args[2:5] + ["..."]))
    result = subprocess.run(args, check=False)
    if result.returncode != 0:
        print(f"Command failed (exit {result.returncode}):", " ".join(args), file=sys.stderr)
    return result.returncode


def main() -> int:
    if not LIGATURES_FILE.is_file():
        print(f"Missing {LIGATURES_FILE}; run harvest_ligatures.py first.", file=sys.stderr)
        return 1

    source = find_source_ttf()
    cached_source = HERE / "material_symbols_rounded.ttf"
    if source != cached_source and not cached_source.is_file():
        shutil.copy2(source, cached_source)
        print(f"Cached source TTF -> {cached_source}")

    src_size = cached_source.stat().st_size
    print(f"Source TTF: {cached_source} ({src_size / 1024 / 1024:.2f} MB)")

    # Harvest the glyph seed once - it is driven by ligature names and the font's
    # substitution tables, both of which are identical between FILL=0 and FILL=1.
    # Only the outline geometry differs.
    wanted_names = {
        line.strip()
        for line in LIGATURES_FILE.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }

    for variant in VARIANTS:
        axes = {"FILL": variant["fill"], **SHARED_AXES}
        print(f"--- Building {variant['name']} variant (FILL={variant['fill']}) ---")

        instance_args = [
            sys.executable, "-m", "fontTools.varLib.instancer",
            str(cached_source),
            *(f"{axis}={value}" for axis, value in axes.items()),
            "--output", str(variant["instanced"]),
        ]
        if run(instance_args) != 0:
            return 1
        inst_size = variant["instanced"].stat().st_size
        print(f"Instanced TTF: {variant['instanced']} ({inst_size / 1024 / 1024:.2f} MB)")

        instanced_font = TTFont(str(variant["instanced"]))
        expanded_glyphs, missing_icons = expand_wanted_icon_names(instanced_font, wanted_names)
        instanced_font.close()
        # Overwrite glyphs_expanded.txt each variant; layout closure is disabled
        # so the expanded seed is the only source of truth for what gets kept.
        GLYPHS_EXPANDED_FILE.write_text("\n".join(expanded_glyphs) + "\n", encoding="utf-8")
        if missing_icons:
            print(
                f"WARNING: {len(missing_icons)} icon names could not be mapped to a ligature in the "
                f"source font (harvester noise or unsupported). First few: {missing_icons[:15]}",
                file=sys.stderr,
            )
        print(
            f"Expanded {len(wanted_names)} icon name lines -> {len(expanded_glyphs)} glyphs "
            f"(ligature outputs + components) -> {GLYPHS_EXPANDED_FILE.name}",
        )

        subset_args = [
            sys.executable, "-m", "fontTools.subset",
            str(variant["instanced"]),
            f"--glyphs-file={GLYPHS_EXPANDED_FILE}",
            f"--output-file={variant['subset']}",
            # Keep ligature substitution + required transforms so the icon
            # ligatures (e.g. "search" -> search glyph) actually fire at runtime.
            "--layout-features+=liga,rlig,clig,calt,ccmp,locl",
            # We already expanded the glyph seed to every ligature *component* for our
            # icons. Leaving layout closure ON would pull in thousands of extra outputs
            # (any ligature whose components are all in a-z once the alphabet is present).
            "--no-layout-closure",
            # Drop heavy bits we don't need: hinting (we render scaled), CFF subroutines.
            "--no-hinting",
            "--desubroutinize",
            "--recommended-glyphs",
            "--notdef-outline",
            "--ignore-missing-glyphs",
            "--ignore-missing-unicodes",
        ]
        if run(subset_args) != 0:
            return 1

        out_size = variant["subset"].stat().st_size
        print(f"Subset TTF: {variant['subset']}")
        print(f"  Source:    {src_size / 1024 / 1024:.2f} MB")
        print(f"  Instanced: {inst_size / 1024 / 1024:.2f} MB")
        print(f"  Subset:    {out_size / 1024:.2f} KB ({out_size * 100 / src_size:.3f}% of source)")

    print("Done. Copy both subset TTFs into app/src/main/res/font/:")
    print("  material_symbols_rounded_subset.ttf          -> material_symbols_rounded.ttf")
    print("  material_symbols_rounded_outlined_subset.ttf -> material_symbols_rounded_outlined.ttf")
    return 0


if __name__ == "__main__":
    sys.exit(main())
