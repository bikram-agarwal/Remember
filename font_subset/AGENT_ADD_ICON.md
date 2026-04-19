# Adding or changing Material Symbols icons (for coding agents)

Remember renders icons with `RememberMaterialRoundedSymbol(name = "ligature_name", …)` using a **subset** TTF at `app/src/main/res/font/material_symbols_rounded.ttf`. If a ligature is missing from that file, the UI shows the raw string or a blank.

## When you change Kotlin only (name already in the subset)

- Add or change the string in `RememberMaterialRoundedSymbol`, `BundledMaterialSymbolIcons.kt` (`symbolName = "…"`), `MainTab` symbol names, `NoteSwipeAction`, `ActionType.materialSymbolName()`, or `name = if (…) "a" else "b"` rows.
- If the ligature was **already** harvested in a prior run, **nothing else** is required.

## When you introduce a **new** ligature name

1. **Confirm** the name is a valid [Material Symbols](https://fonts.google.com/icons) Rounded ligature (underscores, lowercase).

2. **Harvest** (repo root = `Remember/`):

   ```text
   python font_subset/harvest_ligatures.py
   ```

   This rescans `app/src/main/java` and writes `font_subset/ligatures.txt` plus `ligatures_report.json`.  
   If your new name is built only from variables (no string literal the regex can see), either add a literal in Kotlin (e.g. a comment line is NOT enough) or add a small explicit list in `harvest_ligatures.py` (see existing patterns: ternary `name = if`, `ActionType.* -> "…"`, etc.).

3. **Subset** the font:

   ```text
   python font_subset/subset_font.py
   ```

   Requires `fonttools` (`pip install fonttools`). Place the **full** variable `material_symbols_rounded.ttf` in `font_subset/` (from [Google Fonts](https://fonts.google.com/icons) / Material Symbols download), or set `MATERIAL_SYMBOLS_ROUNDED_TTF` to its path. See `subset_font.py` `SOURCE_TTF_CANDIDATES`.

4. **Deploy** the output into the app:

   - Copy `font_subset/material_symbols_rounded_subset.ttf` to  
     `app/src/main/res/font/material_symbols_rounded.ttf`  
     (replace existing).

5. **Build** the app (e.g. `:app:compileGithubDebugKotlin` or your flavor) and verify the icon on device/emulator.

## Icon picker catalog only

If the icon is **only** listed in `BundledMaterialSymbolIcons.kt`, harvesting picks up `symbolName = "…"`. Still run harvest + subset + copy if that symbol was not in the subset before.

## Optional: search behavior

Concept search for the picker lives in `IconPicker.kt` (`searchConceptSynonyms`). Add or adjust synonym lists there when users expect a word (e.g. `"work"`) to surface icons whose **names** do not contain that substring.

## Do not

- Assume a full Material font is bundled; only **subset** glyphs exist.
- Rely on `favorite_border`-style names for a different look at FILL=1; instancing collapses many outline names to filled shapes.

## Suggested symbols to add later (common note-taking / life admin)

Add only what you will use; each new name needs harvest + subset. Verify on [fonts.google.com/icons](https://fonts.google.com/icons) (Rounded) before wiring.

- **Tasks & time:** `task_alt`, `event_available`, `event_busy`, `schedule_send`, `timer`, `hourglass_top`, `hourglass_bottom`, `history`
- **Ideas & writing:** `draw`, `post_add`, `description`, `article`, `psychology`, `science`
- **Money & docs:** `receipt_long`, `account_balance`, `account_balance_wallet`, `payments`, `savings`
- **Travel & places:** `flight`, `hotel`, `luggage`, `pin_drop`, `explore`, `museum`, `park`, `cottage`
- **Health & home:** `water_drop`, `local_fire_department`, `cleaning_services`, `handyman`, `bed`, `shower`, `bathtub`
- **Media:** `image`, `collections`, `mic_external_on`, `podcasts`
- **Pins & priority:** `push_pin`, `keep`, `keep_off`, `priority_high`, `label_important`, `bolt`
