# Translation Guide for Remember

Thank you for your interest in translating Remember! This guide covers everything you need to know to add a new language or improve existing translations.

## Supported Languages

| Language | Locale Code | Resource Folder | Fastlane Folder |
|----------|------------|-----------------|-----------------|
| English (default) | `en` | `values/` | `en-US/` |
| Spanish | `es` | `values-es/` | `es-ES/` |
| French | `fr` | `values-fr/` | `fr-FR/` |
| German | `de` | `values-de/` | `de-DE/` |
| Portuguese | `pt` | `values-pt/` | `pt-BR/` |
| Italian | `it` | `values-it/` | `it-IT/` |

## How to Add a New Language

### 1. Create the resource folder

Create a new folder under `app/src/main/res/values-{locale}/` using the ISO 639-1 language code (e.g., `values-ja/` for Japanese).

### 2. Create `strings.xml`

Copy the structure from `app/src/main/res/values/strings.xml` and translate all strings.

**Important:** Do NOT include any string that has `translatable="false"` in the original file. These 22 strings are excluded from translations:

<details>
<summary>List of translatable="false" strings (click to expand)</summary>

| Category | String names |
|----------|-------------|
| URLs | `settings_about_remember_website_url`, `settings_about_remember_privacy_url`, `settings_about_remember_terms_url`, `settings_about_filepipe_play_store_url`, `settings_about_filepipe_website_url`, `settings_about_obtainx_website_url`, `about_author_github_profile_url` |
| Dev format strings | `dev_options_android_format`, `dev_options_device_format`, `dev_options_database_format`, `dev_options_persisted_uri_grants_format`, `dev_options_db_build_format`, `dev_options_db_version_format`, `dev_options_db_stat_count_with_size` |
| Dev test notification titles | `dev_options_test_notif_low_title`, `dev_options_test_notif_default_title`, `dev_options_test_notif_high_title`, `dev_options_test_notif_big_picture_title`, `dev_options_test_notif_big_text_title`, `dev_options_test_notif_overdue_title` |
| Other | `action_field_mark_done_blank` (empty string), `appearance_preview_sample_text` ("Aa") |
</details>

### 3. Create `strings_icons.xml`

Translate the 18 icon category names. See `app/src/main/res/values/strings_icons.xml` for the source strings.

### 4. Update locale configuration

Add your language to the following files:

- `app/src/main/res/xml/locales_config.xml` — add `<locale android:name="{code}" />`
- `app/build.gradle.kts` — add your code to `resourceConfigurations` list

### 5. Create Fastlane metadata (optional)

Create `fastlane/metadata/android/{locale}/` with:
- `title.txt` (max 50 chars)
- `short_description.txt` (max 80 chars)
- `full_description.txt`
- `changelogs/default.txt`

Use region-specific Fastlane codes (e.g., `ja-JP`, not `ja`).

## Translation Guidelines

### Tone and formality

| Language | Form | Address |
|----------|------|---------|
| Spanish | Informal | `tú` |
| French | Formal | `vous` |
| German | Formal | `Sie` |
| Portuguese (BR) | Informal | `você` |
| Italian | Formal | `Lei` |

### Terminology

Keep these terms untranslated (they are trademarks or standard tech terms):
- **Remember** — the app name (never translate)
- **Markdown** — the markup language
- **PIN** — personal identification number
- **Widget** — Android home screen widget
- **Material You** — Google design system
- **Google Tasks** — Google service name
- **Google Takeout** — Google service name
- **OLED** — display technology

### Format specifiers

**Never modify format specifiers** in translated strings. These must remain exactly as in the original:
- `%1$s` — string placeholder
- `%1$d` — integer placeholder
- `%1$d/%2$d` — multiple placeholders
- `%1$s v%2$s` — version format
- `\'` — escaped apostrophe

### Plurals

All currently supported languages use the same CLDR plural rules: `one` and `other`. If you add a language with different plural rules (e.g., Arabic with `zero`, `few`, `many`), you must add the appropriate `<item>` entries.

### XML escaping

- Escape apostrophes: `\'` (e.g., `Let\'s begin`)
- Escape `&` as `&`
- Escape `<` as `<` and `>` as `>`

## How to Test Translations Locally

1. **Change device language:** Settings → System → Languages → add your language and drag to top
2. **Or use in-app picker:** Settings → Language → select your language (Android 13+)
3. **Run lint:** `./gradlew lintRelease` — check for `MissingTranslation` warnings
4. **Visual testing:** Navigate through all screens and verify no text truncation or layout issues

## How to Submit

1. Fork the repository
2. Create a branch: `git checkout -b feat/translate-{language}`
3. Commit your changes using conventional commits:
   ```
   feat: add {language} translation
   ```
4. Open a Pull Request against `main`
5. In the PR description, list the language code and any translation decisions you made

## Questions?

Open an issue on [GitHub](https://github.com/bikram-agarwal/Remember/issues) with the `translation` label.
