# Developer Runbook

All commands assume PowerShell from the project root (`D:\git\Remember`).

---

## Checkpoints

What to run at each stage of the development loop.

| Checkpoint | Commands |
| --- | --- |
| Tiny code change with unit tests | `.\gradlew.bat :app:compileGithubDebugKotlin`<br>`.\gradlew.bat :app:testGithubDebugUnitTest` |
| Shared code, DB, Hilt, or flavor-sensitive change | `.\gradlew.bat :app:compileGithubDebugKotlin :app:compilePlaystoreDebugKotlin`<br>`.\gradlew.bat :app:testGithubDebugUnitTest :app:testPlaystoreDebugUnitTest` |
| Before commit | `.\gradlew.bat :app:ktlintMainSourceSetCheck :app:compileGithubDebugKotlin :app:testGithubDebugUnitTest` |
| Before release candidate | `.\gradlew.bat :app:ktlintCheck :app:detekt`<br>`.\gradlew.bat :app:testGithubDebugUnitTest :app:testPlaystoreDebugUnitTest`<br>`.\gradlew.bat :app:lintGithubDebug :app:lintVitalGithubDevRelease`<br>`.\gradlew.bat :app:assembleGithubDevRelease` |

---

## Formatting And Dependency Maintenance

Use these when the task is maintenance-focused rather than part of every edit loop.

| Task | Commands |
| --- | --- |
| Check dependency upgrades | `.\gradlew.bat checkDependencyUpdates` |
| Apply dependency upgrades | `.\gradlew.bat --no-parallel --no-configuration-cache versionCatalogUpdate` |
| Update Gradle version | `.\gradlew.bat wrapper --gradle-version <version>` |
| Recheck formatting | `.\gradlew.bat :app:ktlintCheck` |
| Format app Kotlin sources | `.\gradlew.bat :app:ktlintFormat` |
| Format only main app sources | `.\gradlew.bat :app:ktlintMainSourceSetFormat` |
| Lint issue intentionally fixed or suppressed | `.\gradlew.bat :app:updateLintBaseline` — run once, after the code is already correct |

After running a formatter, inspect the diff before keeping the result. Ktlint can touch nearby files that already had style drift, so only keep formatting changes that belong with the current task.

---

## Troubleshooting

| Symptom | Response |
| --- | --- |
| `OutOfMemoryError: Metaspace` during a large command | Split into phases before raising memory limits |
| Metaspace failures after changing Gradle, Kotlin, KSP, Hilt, or Room versions | `.\gradlew.bat --stop` once, then rerun |
| Repeated failures even with split phases | Lower `org.gradle.workers.max` or revisit `org.gradle.jvmargs` |

---

## Appendix: Command Catalog

| Command | What it does |
| --- | --- |
| `:app:compileGithubDebugKotlin` | Compiles Github debug Kotlin sources; runs KSP, Hilt, and Room codegen |
| `:app:compilePlaystoreDebugKotlin` | Same for the Play Store debug flavor |
| `:app:testGithubDebugUnitTest` | Runs Github debug unit tests |
| `:app:testPlaystoreDebugUnitTest` | Runs Play Store debug unit tests |
| `:app:ktlintMainSourceSetCheck` | Formatting check for main sources only — fast |
| `:app:ktlintCheck` | Formatting check across all configured source sets |
| `:app:ktlintMainSourceSetFormat` | Applies ktlint formatting to main sources only |
| `:app:ktlintFormat` | Applies ktlint formatting across all configured app source sets |
| `:app:detekt` | Static analysis |
| `:app:lintGithubDebug` | Full Android lint for the Github debug variant |
| `:app:lintVitalGithubDevRelease` | Release-vital lint — used in release candidate checks |
| `:app:updateLintBaseline` | Rewrites `app/lint-baseline.xml` — run only after fixing or intentionally suppressing findings |
| `:app:assembleGithubDebug` | Builds the Github debug APK |
| `:app:assembleGithubDevRelease` | Builds the minified Github dev-release APK — expensive |
| `:app:installGithubDebug` | Builds and installs Github debug on a connected device |
| `:app:installPlaystoreDebug` | Builds and installs Play Store debug on a connected device |
| `:app:installGithubDevRelease` | Builds and installs the minified dev-release on a connected device |
| `:app:uninstallGithubDebug` | Removes Github debug from the connected device |
| `:app:uninstallPlaystoreDebug` | Removes Play Store debug from the connected device |
| `checkDependencyUpdates` | Reports available stable dependency updates |
| `dependencyUpdates` | Raw dependency-updates report; use the flags above for this project |
| `tasks` | Lists common Gradle tasks |
| `tasks --all` | Lists every generated task — output is noisy |

---

## Appendix: ObtainX

### Quality Check Mapping

| Command | What it does |
| --- | --- |
| `flutter analyze` <br> `dart format --output=none --set-exit-if-changed .` | Runs static analysis checks (via analysis_options.yaml) and verifies files match standard Dart formatting guidelines.|
| `flutter test` | Executes all unit and widget tests located in the test/ directory. |
| `dart format .` | Formats all .dart files in the workspace. |
| `flutter build apk --split-per-abi` | Compiles a release APK, performing full tree-shaking and build verification.

### Development Loop Checkpoints

| Checkpoint | Commands |
| --- | --- |
| Tiny code change | `flutter analyze` |
| Logic change with unit tests | `flutter test` |
| Before commit | `dart format .` <br> `flutter analyze` <br> `flutter test` |
| Before PR / Release Candidate | `flutter analyze` <br> `flutter test` <br> `flutter build apk --split-per-abi`|
