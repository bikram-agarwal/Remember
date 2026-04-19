"""Resolve Material Symbols icon strings to ligature output + component glyphs for subsetting."""

from __future__ import annotations

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otTables

DIGIT_TO_GLYPH = {
    "0": "digit_zero",
    "1": "digit_one",
    "2": "digit_two",
    "3": "digit_three",
    "4": "digit_four",
    "5": "digit_five",
    "6": "digit_six",
    "7": "digit_seven",
    "8": "digit_eight",
    "9": "digit_nine",
}


def icon_name_to_component_sequence(icon_name: str) -> tuple[str, ...]:
    """Map UI icon name (e.g. delete_outline) to GSUB ligature component glyph names."""
    glyphs: list[str] = []
    for character in icon_name:
        if character == "_":
            glyphs.append("underscore")
        elif character.isdigit():
            glyphs.append(DIGIT_TO_GLYPH[character])
        elif character.islower():
            glyphs.append(character)
        else:
            raise ValueError(f"Unsupported character {character!r} in icon name {icon_name!r}")
    return tuple(glyphs)


def build_ligature_sequence_index(font: TTFont) -> dict[tuple[str, ...], str]:
    """Map full ligature component sequence (first glyph + rest) -> LigGlyph name."""
    index: dict[tuple[str, ...], str] = {}
    for lookup in font["GSUB"].table.LookupList.Lookup:
        if not lookup:
            continue
        for sub in lookup.SubTable:
            if not sub:
                continue
            inner = getattr(sub, "ExtSubTable", sub)
            if not isinstance(inner, otTables.LigatureSubst):
                continue
            for first_key, seqs in inner.ligatures.items():
                for seq in seqs:
                    full = (first_key, *tuple(seq.Component))
                    index[full] = seq.LigGlyph
    return index


def expand_wanted_icon_names(
    font: TTFont,
    wanted_icon_names: set[str],
) -> tuple[list[str], list[str]]:
    """
    Return sorted unique glyph names to pass to pyftsubset --glyphs-file, plus a list
    of icon names that could not be mapped (harvester noise or unsupported names).
    """
    glyph_order = set(font.getGlyphOrder())
    ligature_index = build_ligature_sequence_index(font)
    expanded: set[str] = set()
    missing: list[str] = []

    for raw_name in sorted(wanted_icon_names):
        name = raw_name.strip()
        if not name or name.startswith("#"):
            continue
        try:
            sequence = icon_name_to_component_sequence(name)
        except ValueError:
            missing.append(name)
            continue

        ligature_output = ligature_index.get(sequence)
        if ligature_output is None:
            missing.append(name)
            continue

        needed = {ligature_output, *sequence}
        unknown = {glyph_name for glyph_name in needed if glyph_name not in glyph_order}
        if unknown:
            missing.append(f"{name}::missing::{sorted(unknown)}")
            continue

        expanded.update(needed)

    return sorted(expanded), missing
