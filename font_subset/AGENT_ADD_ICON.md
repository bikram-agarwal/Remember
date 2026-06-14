# Adding or changing Material Symbols icons (for coding agents)

Remember renders icons with `RememberMaterialRoundedSymbol(name = "ligature_name", …)` using **two** subset TTFs in `app/src/main/res/font/`:

- `material_symbols_rounded.ttf` - filled variant (FILL=1, default)
- `material_symbols_rounded_outlined.ttf` - outlined variant (FILL=0)

Pass `filled = false` on `RememberMaterialRoundedSymbol` to render from the outlined TTF. Inside the app, prefer `filled = <state>` over the `if (state) "icon" else "icon_border"` pattern - the instanced subsets bake FILL into geometry, so a `_border` alt name alone will look identical to the filled glyph. Switching the font family is the only way to get a visually distinct outline.

If a ligature is missing from either file, the UI shows the raw string or a blank.

## When you change Kotlin only (name already in the subset)

- Add or change the string in `RememberMaterialRoundedSymbol`, `BundledMaterialSymbolIcons.kt` (`symbolName = "…"`), `MainTab` symbol names, `NoteSwipeAction`, `ActionType.materialSymbolName()`, or `name = if (…) "a" else "b"` rows.
- If the ligature was **already** harvested in a prior run, **nothing else** is required.

## When you introduce a **new** ligature name

1. **Confirm** the name is a valid [Material Symbols](https://fonts.google.com/icons) Rounded ligature (underscores, lowercase).

2. **Build the icon fonts** (repo root = `Remember/`):

   ```text
   python font_subset/build_icon_font.py
   ```

   This rescans `app/src/main/java`, subsets the variable font down to the icons it finds, copies both generated subset fonts into `app/src/main/res/font/`, and deletes the scratch/report files it created (`*_instanced.ttf`, `*_subset.ttf`, `glyphs_expanded.txt`, `ligatures.txt`, `ligatures_report.json`, `probe.txt`, `__pycache__/`).

   Requires `fonttools` (`pip install fonttools`). Place the **full** variable `material_symbols_rounded.ttf` in `font_subset/` (from [Google Fonts](https://fonts.google.com/icons) / Material Symbols download), or set `MATERIAL_SYMBOLS_ROUNDED_TTF` to its path (see `build_icon_font.py` `SOURCE_TTF_CANDIDATES`).

   Stage flags if you need them: `--harvest-only` (scan + write `ligatures.txt` only; no `fonttools` needed) or `--skip-harvest` (subset an existing `ligatures.txt`). If your new name is built only from variables (no string literal the regex can see), either add a literal in Kotlin (a comment line is NOT enough) or add a small explicit pattern in `build_icon_font.py` (see existing patterns: ternary `name = if`, `ActionType.* -> "…"`, etc.).

3. **Build** the app (e.g. `:app:compileGithubDebugKotlin` or your flavor) and verify the icon on device/emulator.

## Icon picker catalog only

If the icon is **only** listed in `BundledMaterialSymbolIcons.kt`, harvesting picks up `symbolName = "…"`. Still run `build_icon_font.py` if that symbol was not in the subset before.

## Optional: search behavior

Concept search for the picker is data-driven, fed by two committed JSON files under `app/src/main/res/raw/`:

- `emojis.json` - the picker's emoji list with CLDR keywords (`fire` -> `flame`, `hot`, `lit`, `tool`).
- `icon_keywords.json` - per-ligature Material Symbols tags (`fitness_center` -> `gym`, `workout`, `dumbbell`).

Both are emitted by Python scripts in `font_subset/`. Re-run them whenever upstream Unicode/CLDR or Material Symbols metadata changes (typically once a year), or after you add/remove icons in `BundledMaterialSymbolIcons.kt`:

```text
python font_subset/build_search_emojis.py     # writes app/src/main/res/raw/emojis.json
python font_subset/build_search_keywords.py   # writes app/src/main/res/raw/icon_keywords.json
```

Both scripts use stdlib only (no `pip install` required) and cache their downloads under `font_subset/cache/`. Pass `--force-refresh` to bypass the cache. Pass `--emoji-version`, `--cldr-branch`, or `--metadata-url` to pin a specific upstream snapshot.

Ranking lives in `IconPickerSearch.kt`. The scorer is word-boundary-aware (so `lit` does **not** match inside `light`), supports plural/tense stemming (`songs` -> `song`, `running` -> `run`), and Levenshtein-1 fuzzy matching for typos on tokens >= 4 characters (`calender` -> `calendar`). Field weights live in `IconPicker.kt` (`FIELD_WEIGHT_NAME` etc.) so name hits outrank tag hits which outrank category hits. Adjust the constants there if a particular query needs reweighting.

If a concept query returns nothing useful even though the right emoji/icon exists (e.g. CLDR doesn't tag a specific synonym you expect), the cleanest fix is to add the term to the upstream JSON via a small augmentation step in the Python script, **not** by re-introducing a hand-curated synonym map in Kotlin.

## Do not

- Assume a full Material font is bundled; only **subset** glyphs exist.
- Rely on `favorite_border`-style names for a different look at FILL=1; instancing collapses many outline names to filled shapes.
