# Plan: Multi-Language Support for Remember

> **Reviewed:** 2026-07-10 — Double-checked against actual source code.
> Corrections applied: string count, AppCompat dependency, locale strategy, translatable=false list, Fastlane Fastfile analysis, date formatting audit, minSdk constraint.

## Overview

This document outlines the complete plan to add multi-language support (i18n) to the Remember Android app. The app is built with Kotlin + Jetpack Compose and currently only supports English (`values/strings.xml`).

### Target Languages

| Language | Locale Code | Folder | Fastlane folder |
|----------|------------|--------|-----------------|
| English (default) | `en` | `values/` (existing) | `en-US/` (existing) |
| Spanish | `es` | `values-es/` | `es-ES/` |
| French | `fr` | `values-fr/` | `fr-FR/` |
| German | `de` | `values-de/` | `de-DE/` |
| Portuguese | `pt` | `values-pt/` | `pt-BR/` |
| Italian | `it` | `values-it/` | `it-IT/` |

### Current State Analysis (Verified)

| Aspect | Finding | Source |
|--------|---------|--------|
| **strings.xml** | **963 `<string>` entries** (not ~450) | `Select-String` count |
| **plurals** | **37 `<plurals>` entries** (not ~30) | `Select-String` count |
| **strings_icons.xml** | 18 icon category names | `grep` count |
| **translatable="false"** | **22 strings** — must be excluded from all translated files | `grep_search` |
| **Hardcoded strings** | Only 1 needs migration (`OptionsPanel.kt:553`) | `grep_search` |
| **Date/time formatting** | Uses `DateFormat.getTimeFormat(context)` (locale-aware) and `DateTimeFormatter.ofPattern(pattern, Locale.getDefault())` in **4 files** (`SnoozeActivity.kt`, `ReminderPicker.kt`, `NoteCard.kt`, `GoogleTasksImportScreen.kt`); `DateTimeFormatting.kt` uses `DateFormat` (not `DateTimeFormatter`) | `grep_search` (~30 locale matches: ~15 `Locale.getDefault()` + ~15 `Locale.US`) |
| **Locale-independent code** | `Locale.US` for hex colors, backup filenames, tag sorting — correct, no change needed | `grep_search` |
| **Fastlane metadata** | Only `en-US/` folder exists | `glob` |
| **Fastfile** | Only uploads `en-US` screenshots/images — no multi-locale screenshot support | `read_file` |
| **AndroidManifest.xml** | No `localeConfig` attribute; already has `tools:targetApi="tiramisu"` | `read_file` |
| **build.gradle.kts** | No `resourceConfigurations`; **no `appcompat` dependency** | `read_file` |
| **Version catalog** | `libs.versions.toml` — **no `appcompat` entry** | `read_file` |
| **minSdk** | **31** (Android 12) — affects language picker strategy | `build.gradle.kts` |
| **targetSdk** | 37 | `build.gradle.kts` |
| **No `LocaleManager`** | No existing language picker or locale management code | `grep_search` |

---

## Phase 1: Fix Hardcoded Strings

Before adding translations, clean up the 1 hardcoded user-facing string found in the Kotlin source.

### Task 1.1: Migrate "+X more" in OptionsPanel.kt

**File:** `app/src/main/java/dev/bikram/remember/ui/edit/OptionsPanel.kt`, line 553

**Current:**
```kotlin
text = "+${reminders.size - 1} more",
```

**Action:**
1. Add to `values/strings.xml` in the "Editor options row" section:
   ```xml
   <string name="options_reminders_more">+%1$d more</string>
   ```
2. Update `OptionsPanel.kt:553` to:
   ```kotlin
   text = stringResource(R.string.options_reminders_more, reminders.size - 1),
   ```

### Other hardcoded strings (no action needed)

The remaining 13 hardcoded strings are all locale-neutral symbols or numeric defaults:
- Bullet/separator characters: `"·"`, `"•"`, `"\u2022"` — identical in all languages
- Hashtag prefixes: `"#$text"` — not translated
- Numeric defaults: `"1"`, `"10"` — not translated
- Numbered list markers: `"$index."` — locale-neutral
- Single-character symbols: `"?"`, `"Aa"` (already `translatable="false"`)

---

## Phase 2: Create Translation Files

### Strings to EXCLUDE from translations (22 `translatable="false"` entries)

These must **not** appear in any `values-{lang}/strings.xml` file:

| Category | String names |
|----------|-------------|
| **URLs** (8) | `settings_about_remember_website_url`, `settings_about_remember_privacy_url`, `settings_about_remember_terms_url`, `settings_about_filepipe_play_store_url`, `settings_about_filepipe_website_url`, `settings_about_obtainx_website_url`, `about_author_github_profile_url`, `settings_about_obtainx_website_url` |
| **Dev format strings** (7) | `dev_options_android_format`, `dev_options_device_format`, `dev_options_database_format`, `dev_options_persisted_uri_grants_format`, `dev_options_db_build_format`, `dev_options_db_version_format`, `dev_options_db_stat_count_with_size` |
| **Dev test notification titles** (6) | `dev_options_test_notif_low_title`, `dev_options_test_notif_default_title`, `dev_options_test_notif_high_title`, `dev_options_test_notif_big_picture_title`, `dev_options_test_notif_big_text_title`, `dev_options_test_notif_overdue_title` |
| **Other** (1) | `action_field_mark_done_blank` (empty string), `appearance_preview_sample_text` ("Aa") |

**Total translatable strings per language: 963 - 22 = 941 strings + 37 plurals = 978 entries per language.**

### Task 2.1: Create `values-es/strings.xml` (Spanish)

Create `app/src/main/res/values-es/strings.xml` with all 941 translatable strings + 37 plurals.

**Translation guidelines:**
- Use informal `tú` form (standard for Android apps in Spanish)
- Keep format specifiers (`%1$s`, `%1$d`, `%1$d/%2$d`) exactly as-is
- Do **not** include any `translatable="false"` strings
- Translate plurals with `quantity="one"` / `quantity="other"` forms
- Keep XML comments as section headers for maintainability
- Keep `app_name` as "Remember" (trademark)

**Key terminology:**
| English | Spanish |
|---------|---------|
| Reminder | Recordatorio |
| Snooze | Posponer |
| Notes | Notas |
| Checklist | Lista de verificación |
| Archive | Archivar |
| Trash | Papelera |
| Starred | Destacadas |
| Tags | Etiquetas |
| Widget | Widget (keep) |
| Markdown | Markdown (keep) |
| PIN | PIN (keep) |
| Biometric | Biométrico |

### Task 2.2: Create `values-fr/strings.xml` (French)

**Key terminology:**
| English | French |
|---------|--------|
| Reminder | Rappel |
| Snooze | Reporter |
| Notes | Notes |
| Checklist | Liste de contrôle |
| Archive | Archiver |
| Trash | Corbeille |
| Starred | Favoris |
| Tags | Étiquettes |

- Use formal `vous` form

### Task 2.3: Create `values-de/strings.xml` (German)

**Key terminology:**
| English | German |
|---------|--------|
| Reminder | Erinnerung |
| Snooze | Snoozefunktion |
| Notes | Notizen |
| Checklist | Checkliste |
| Archive | Archiv |
| Trash | Papierkorb |
| Starred | Markiert |
| Tags | Tags (keep, common in German tech) |

- Use formal `Sie` form
- **Plural note:** German CLDR uses `one`/`other` — same as the other target languages. No `zero` form is needed for the plurals in this app.

### Task 2.4: Create `values-pt/strings.xml` (Portuguese)

**Key terminology:**
| English | Portuguese |
|---------|------------|
| Reminder | Lembrete |
| Snooze | Adiar |
| Notes | Notas |
| Checklist | Lista de verificação |
| Archive | Arquivar |
| Trash | Lixeira |
| Starred | Favoritas |
| Tags | Etiquetas |

- Use Brazilian Portuguese conventions with `você` form

### Task 2.5: Create `values-it/strings.xml` (Italian)

**Key terminology:**
| English | Italian |
|---------|---------|
| Reminder | Promemoria |
| Snooze | Rimanda |
| Notes | Note |
| Checklist | Lista di controllo |
| Archive | Archivio |
| Trash | Cestino |
| Starred | Preferiti |
| Tags | Etichette |

- Use formal `Lei` form

### Task 2.6: Create `values-{lang}/strings_icons.xml` for each language

Translate the 18 icon category names in `strings_icons.xml` for each of the 5 languages.

| English | Spanish | French | German | Portuguese | Italian |
|---------|---------|--------|--------|------------|---------|
| Notes & writing | Notas y escritura | Notes et écriture | Notizen & Schreiben | Notas e escrita | Note e scrittura |
| Tasks, dates & priority | Tareas, fechas y prioridad | Tâches, dates et priorité | Aufgaben, Daten & Priorität | Tarefas, datas e prioridade | Attività, date e priorità |
| Money & bills | Dinero y facturas | Argent et factures | Geld & Rechnungen | Dinheiro e contas | Soldi e bollette |
| Home & chores | Hogar y tareas | Maison et corvées | Haushalt & Aufgaben | Casa e tarefas | Casa e faccende |
| Travel & places | Viajes y lugares | Voyage et lieux | Reisen & Orte | Viagens e lugares | Viaggi e luoghi |
| Food & drinks | Comida y bebidas | Nourriture et boissons | Essen & Trinken | Comida e bebidas | Cibo e bevande |
| Health, wellness & sports | Salud, bienestar y deportes | Santé, bien-être et sport | Gesundheit, Wellness & Sport | Saúde, bem-estar e esportes | Salute, benessere e sport |
| Media & entertainment | Medios y entretenimiento | Médias et divertissement | Medien & Unterhaltung | Mídia e entretenimento | Media e intrattenimento |
| People & communication | Personas y comunicación | Personnes et communication | Personen & Kommunikation | Pessoas e comunicação | Persone e comunicazione |
| Devices & tech | Dispositivos y tecnología | Appareils et technologie | Geräte & Technik | Dispositivos e tecnologia | Dispositivi e tecnologia |
| Weather & Misc | Clima y varios | Météo et divers | Wetter & Verschiedenes | Clima e diversos | Meteo e vari |
| Brands | Marcas | Marques | Marken | Marcas | Marchi |
| Google | Google | Google | Google | Google | Google |
| Streaming | Streaming | Streaming | Streaming | Streaming | Streaming |
| Money | Dinero | Argent | Geld | Dinheiro | Soldi |
| Social | Social | Social | Social | Social | Social |
| Games | Juegos | Jeux | Spiele | Jogos | Giochi |
| Others | Otros | Autres | Andere | Outros | Altri |

---

## Phase 3: Android Locale Configuration

### Task 3.1: Create `locales_config.xml`

Create `app/src/main/res/xml/locales_config.xml` (folder `xml/` already exists with 7 files):

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="es" />
    <locale android:name="fr" />
    <locale android:name="de" />
    <locale android:name="pt" />
    <locale android:name="it" />
</locale-config>
```

### Task 3.2: Add `localeConfig` to AndroidManifest.xml

The `<application>` tag already has `tools:targetApi="tiramisu"`. Add `android:localeConfig`:

```xml
<application
    android:name=".RememberApp"
    android:allowBackup="true"
    android:localeConfig="@xml/locales_config"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... >
```

This enables Android 13+ per-app language preferences in Settings > System > Languages & Input > App Languages.

### Task 3.3: Add `resourceConfigurations` to build.gradle.kts

In `app/build.gradle.kts`, within the `defaultConfig { ... }` block, add:

```kotlin
resourceConfigurations += listOf("en", "es", "fr", "de", "pt", "it")
```

This strips unused locale resources from the final APK, reducing size. Since the project uses `isShrinkResources = true` in release builds, this works with R8 resource shrinking.

---

## Phase 4: In-App Language Picker

### ⚠️ Correction from original plan

The original plan suggested using `AppCompatDelegate.setApplicationLocales()`. **This is incorrect** — the project does **not** depend on `androidx.appcompat:appcompat` and uses pure Jetpack Compose. Adding AppCompat just for locale switching would be over-engineering.

### Revised approach: `LocaleManager` (Android 13+) + `AppCompatDelegate` is NOT needed

Since `minSdk = 31` (Android 12) and `targetSdk = 37`, the strategy is:

| Android version | Approach |
|----------------|----------|
| Android 13+ (API 33+) | `LocaleManager.getInstance(context).applicationLocales = LocaleList.forLanguageTags("es")` — native API, no library needed |
| Android 12 (API 31-32) | `AppCompatDelegate.setApplicationLocales()` — **requires adding `androidx.appcompat:appcompat:1.6+`** OR use `Configuration` override via `context.createConfigurationContext()` |

**Recommended approach for Android 12 support:**

Since the project is pure Compose (no AppCompat), the simplest option is:

1. **For Android 13+:** Use `LocaleManager` (no dependency needed)
2. **For Android 12:** Use `Configuration` + `recreate()`:
   ```kotlin
   @Suppress("DEPRECATION")
   private fun setLocaleAndroid12(context: Context, language: String) {
       val locale = Locale(language)
       Locale.setDefault(locale)
       val config = Configuration(context.resources.configuration)
       config.setLocale(locale)
       context.createConfigurationContext(config).resources
       (context as? Activity)?.recreate()
   }
   ```

**Alternatively**, add `androidx.appcompat:appcompat:1.7.0` to get `AppCompatDelegate.setApplicationLocales()` for backward compatibility. This is the officially recommended approach by Google, but adds a dependency to a Compose-only project.

### Task 4.1: Add `androidx.appcompat:appcompat` dependency (OPTIONAL)

If AppCompat approach is chosen, add to `libs.versions.toml`:
```toml
[versions]
appcompat = "1.7.0"

[libraries]
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
```

And in `app/build.gradle.kts`:
```kotlin
implementation(libs.androidx.appcompat)
```

### Task 4.2: Create `LanguageSettingsScreen.kt`

- New Composable screen accessible from Settings
- Shows current language with a radio-button list
- Options: "System default", "English", "Español", "Français", "Deutsch", "Português", "Italiano"
- On selection:
  - Android 13+: `LocaleManager.getInstance(context).applicationLocales = LocaleList.forLanguageTags(lang)`
  - Android 12: `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))` (if AppCompat added) OR `Configuration` override

### Task 4.3: Add settings strings

Add to `values/strings.xml`:
```xml
<string name="settings_language">Language</string>
<string name="settings_language_desc">Choose the app language</string>
<string name="settings_language_system_default">System default</string>
```

And translations in each `values-{lang}/strings.xml`:

| String | es | fr | de | pt | it |
|--------|----|----|----|----|-----|
| settings_language | Idioma | Langue | Sprache | Idioma | Lingua |
| settings_language_desc | Elige el idioma de la app | Choisir la langue de l'application | App-Sprache wählen | Escolha o idioma do app | Scegli la lingua dell'app |
| settings_language_system_default | Predeterminado del sistema | Par défaut du système | Systemstandard | Padrão do sistema | Predefinito del sistema |

---

## Phase 5: Fastlane Metadata Translation

### ⚠️ Correction from original plan

The `Fastfile` only uploads screenshots from `en-US/images/`. Translating metadata (title, descriptions) is sufficient for Play Store listing localization. **Screenshots do not need to be duplicated** per language — Play Store reuses the default (`en-US`) screenshots when no localized screenshots are provided. The `Fastfile` does not need modification.

### Task 5.1: Create Fastlane folders

```
fastlane/metadata/android/
├── en-US/          (existing — screenshots + metadata)
├── es-ES/          (new — metadata only, no screenshots)
├── fr-FR/          (new — metadata only)
├── de-DE/          (new — metadata only)
├── pt-BR/          (new — metadata only)
└── it-IT/          (new — metadata only)
```

Each new folder needs 4 files:
- `title.txt`
- `short_description.txt`
- `full_description.txt`
- `changelogs/default.txt`

### Task 5.2: Translate `title.txt` (max 50 chars)

| Language | Title |
|----------|-------|
| en-US | Remember: Notes & Reminders |
| es-ES | Remember: Notas y Recordatorios |
| fr-FR | Remember : Notes et Rappels |
| de-DE | Remember: Notizen & Erinnerungen |
| pt-BR | Remember: Notas e Lembretes |
| it-IT | Remember: Note e Promemoria |

### Task 5.3: Translate `short_description.txt` (max 80 chars)

| Language | Short Description | Chars |
|----------|-------------------|-------|
| en-US | Persistent recurring reminders that return if accidentally dismissed | 68 |
| es-ES | Recordatorios persistentes que vuelven si los descartas sin querer | 65 |
| fr-FR | Rappels persistants qui reviennent s'ils sont accidentellement ignorés | 72 |
| de-DE | Beständige Erinnerungen, die zurückkommen, wenn versehentlich verworfen | 72 |
| pt-BR | Lembretes persistentes que retornam se acidentalmente dispensados | 66 |
| it-IT | Promemoria persistenti che tornano se eliminati per sbaglio | 62 |

### Task 5.4: Translate `full_description.txt`

Translate the complete Play Store / F-Droid full description for each language. The description uses HTML formatting (`<b>` tags) and emoji section headers — these must be preserved as-is.

**Translation notes:**
- Keep all `<b>━━ 📌 ... ━━</b>` formatting intact
- Keep emoji section headers
- Translate the descriptive paragraphs between headers
- Keep "Remember" as the app name (trademark, per LICENSE file)
- Keep "Markdown", "Google Tasks", "Google Takeout" as-is
- Keep "Material You" as-is (Google brand)
- Keep "OLED" as-is

### Task 5.5: Translate `changelogs/default.txt`

Current content is a URL pointer:
```
See changelog at https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md
```

| Language | Changelog text |
|----------|---------------|
| es-ES | Ver changelog en https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md |
| fr-FR | Voir le journal des modifications sur https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md |
| de-DE | Changelog unter https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md |
| pt-BR | Ver changelog em https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md |
| it-IT | Vedi changelog su https://github.com/bikram-agarwal/Remember/blob/main/docs/CHANGELOG.md |

---

## Phase 6: Testing & Verification

### Task 6.1: Lint check for missing translations

```bash
./gradlew lintRelease
```

Check for `MissingTranslation` warnings. The 22 `translatable="false"` strings should not appear.

### Task 6.2: Visual testing

Change device language to each supported locale and verify:

- [ ] Home screen (notes, filters, sort options)
- [ ] Note editor (toolbar, markdown editor, checklist)
- [ ] Reminder picker (date/time, recurrence, snooze presets)
- [ ] Settings (all sections: appearance, security, backup, updates, about)
- [ ] Widgets (agenda, starred, quick capture)
- [ ] Notifications (reminder, summary, quick capture)
- [ ] Onboarding screens
- [ ] Lock screen / PIN setup
- [ ] Tag editor
- [ ] Icon picker (category names)
- [ ] Google Tasks import
- [ ] Developer options
- [ ] History (archive, trash)
- [ ] Date/time formatting (day/month names in correct locale)

### Task 6.3: Plural forms testing

Test plural strings with quantities 0, 1, 2, 5, 100 in each language.

**Note:** All 5 target languages (es, fr, de, pt, it) use the same `one`/`other` CLDR plural rules. No `zero`, `few`, or `many` forms are needed.

### Task 6.4: Layout testing

- [ ] No text truncation in German (typically 30% longer than English)
- [ ] No layout breaks in Italian
- [ ] Date/time formatting uses correct locale names for days/months
- [ ] Notification text fits in expanded notification shade
- [ ] Widget text doesn't overflow

### Task 6.5: Fastlane validation

```bash
fastlane supply --validate
```

Verify all metadata files are valid for each language.

---

## Phase 7: Documentation & PR

### Task 7.1: Update README.md

Add a "Languages" section listing supported languages and how to contribute translations.

### Task 7.2: Create translation contribution guide

Create `docs/TRANSLATIONS.md` with:
- How to add a new language
- Translation guidelines (tone, formality, terminology)
- How to test translations locally
- How to submit via PR
- List of `translatable="false"` strings that must not be translated

### Task 7.3: Create branch and PR

```bash
git checkout -b feat/multi-language-support
git add -A
git commit -m "feat: add multi-language support (es, fr, de, pt, it)

- Add translated strings.xml for Spanish, French, German, Portuguese, Italian
- Add translated strings_icons.xml for each language
- Add locales_config.xml for Android 13+ per-app language
- Fix hardcoded string in OptionsPanel.kt
- Add Fastlane metadata translations for all 5 languages
- Add in-app language picker
- Add translation contribution guide"
git push fork feat/multi-language-support
```

Then create a PR from `diegoalgg88/Remember:feat/multi-language-support` to `bikram-agarwal/Remember:main`.

---

## Summary: File Creation/Modification Checklist

### New files to create (31 files)

**strings.xml translations (5 files):**
1. `app/src/main/res/values-es/strings.xml`
2. `app/src/main/res/values-fr/strings.xml`
3. `app/src/main/res/values-de/strings.xml`
4. `app/src/main/res/values-pt/strings.xml`
5. `app/src/main/res/values-it/strings.xml`

**strings_icons.xml translations (5 files):**
6. `app/src/main/res/values-es/strings_icons.xml`
7. `app/src/main/res/values-fr/strings_icons.xml`
8. `app/src/main/res/values-de/strings_icons.xml`
9. `app/src/main/res/values-pt/strings_icons.xml`
10. `app/src/main/res/values-it/strings_icons.xml`

**Locale config (1 file):**
11. `app/src/main/res/xml/locales_config.xml`

**In-app language picker (1 file, optional):**
12. `app/src/main/java/dev/bikram/remember/ui/settings/LanguageSettingsScreen.kt`

**Fastlane metadata (20 files = 4 files × 5 languages):**
13-16. `fastlane/metadata/android/es-ES/{title,short_description,full_description,changelogs/default}.txt`
17-20. `fastlane/metadata/android/fr-FR/{...}.txt`
21-24. `fastlane/metadata/android/de-DE/{...}.txt`
25-28. `fastlane/metadata/android/pt-BR/{...}.txt`
29-32. `fastlane/metadata/android/it-IT/{...}.txt`

**Documentation (2 files):**
33. `docs/TRANSLATIONS.md`
34. This plan file (`docs/I18N_PLAN.md` — already exists)

### Files to modify (5 files)

1. `app/src/main/res/values/strings.xml` — add `options_reminders_more` string + language settings strings
2. `app/src/main/AndroidManifest.xml` — add `android:localeConfig="@xml/locales_config"`
3. `app/build.gradle.kts` — add `resourceConfigurations` in `defaultConfig` (and optionally `appcompat` dependency)
4. `app/src/main/java/.../OptionsPanel.kt` — use `stringResource()` for "+X more" text
5. `gradle/libs.versions.toml` — add `appcompat` version + library entry (if AppCompat approach chosen)
6. `README.md` — add Languages section

### Estimated effort

| Phase | Effort | Description |
|-------|--------|-------------|
| Phase 1 | Small | 1 string fix |
| Phase 2 | **Very Large** | **941 strings + 37 plurals × 5 languages = ~4,890 translation entries** |
| Phase 3 | Small | 2 config changes |
| Phase 4 | Medium | Language picker (optional, requires architecture decision) |
| Phase 5 | Medium | 4 metadata files × 5 languages |
| Phase 6 | Medium | Testing across 6 locales |
| Phase 7 | Small | Documentation + PR |

### Key risks

- **Translation volume**: ~4,890 entries is significant. Consider prioritizing user-facing strings first, or using a translation service.
- **Translation quality**: Machine translations need human review for context-specific terms (snooze presets, reminder recurrence patterns, notification actions).
- **Trademark**: "Remember" must not be translated (per LICENSE & Trademark Notice in README).
- **Fastlane locale codes**: Must use region-specific codes (`es-ES`, `fr-FR`, `de-DE`, `pt-BR`, `it-IT`) — not bare language codes.
- **German string length**: German text is typically 30% longer than English — verify no truncation in UI components with fixed widths.
- **AppCompat decision**: Adding `appcompat` to a pure-Compose project is a trade-off. The alternative (`Configuration` override) is more code but no dependency.
- **Fastfile**: No modification needed — Play Store reuses `en-US` screenshots for locales without their own.

### Corrections applied during double-check review

| # | Original plan said | Actual finding | Fix |
|---|-------------------|----------------|-----|
| 1 | ~450 strings | 963 strings | Updated count |
| 2 | ~30 plurals | 37 plurals | Updated count |
| 3 | Use `AppCompatDelegate.setApplicationLocales()` | No `appcompat` dependency exists | Added dependency note + alternative |
| 4 | Fastfile needs modification for multi-locale screenshots | Fastfile only uploads `en-US` screenshots, Play Store reuses them | Removed Fastfile modification requirement |
| 5 | "All 5 languages use one/other plurals" (vague) | Confirmed: all 5 use CLDR `one`/`other` only | Added explicit CLDR note |
| 6 | Did not list `translatable="false"` strings | 22 strings identified | Added complete exclusion table |
| 7 | Did not mention `minSdk = 31` constraint | minSdk 31 affects language picker strategy | Added Android 12 vs 13 strategy |
| 8 | Did not audit date formatting code | **4 files** use `DateTimeFormatter.ofPattern` with `Locale.getDefault()` (`SnoozeActivity.kt`, `ReminderPicker.kt`, `NoteCard.kt`, `GoogleTasksImportScreen.kt`); `DateTimeFormatting.kt` uses `DateFormat` (not `DateTimeFormatter`) — all locale-aware, no change needed | Added to analysis, confirmed no change needed |
| 9 | Did not mention `Locale.US` usage | Used for hex colors, backup filenames, tag sorting — correct | Added to analysis |
| 10 | Estimated ~2,250 translation entries | Actual: ~4,890 entries | Updated estimate |
| 11 | "6 files" use `DateTimeFormatter.ofPattern` | **4 files** use `DateTimeFormatter.ofPattern` with `Locale.getDefault()` (`SnoozeActivity.kt`, `ReminderPicker.kt`, `NoteCard.kt`, `GoogleTasksImportScreen.kt`); `DateTimeFormatting.kt` uses `DateFormat.getTimeFormat()` (not `DateTimeFormatter`) — counted erroneously as a 5th file; 6th file unidentified | Corrected count to 4 files in Current State Analysis table |
| 12 | "34 locale matches" | Actual: **~30 matches** (~15 `Locale.getDefault()` + ~15 `Locale.US` across 9 files); the count of 34 likely included import statements or comments | Corrected to ~30 in Current State Analysis table |
| 13 | `GoogleTasksImportScreen.kt:2179` implicitly included in DateTimeFormatter count | Uses `DateTimeFormatter.ofPattern("MMM d")` **without** explicit `Locale.getDefault()` — relies on default locale resolution; still locale-aware but worth noting for completeness | Added explicit file name to Current State Analysis table; no code change needed |
