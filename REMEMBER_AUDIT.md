# Remember - Senior Android audit (April 2026)

App version audited: 0.6.0 (versionCode 60)
AGP 9.2, Kotlin 2.3.21, Compose BOM 2026.04.01, Material3 Expressive 1.5.0-alpha18, minSdk 30, targetSdk 36.

The codebase is in genuinely good shape - Hilt + Room + DataStore + Compose, baseline profile wired, two flavors, Glance widget, KSP, ktlint + detekt, schema export, R8 + shrinkResources. Below is what would push it from "well-built" into "showcase".

---

## [x] 1. Material 3 Expressive surface area Claude is leaving on the table

The project already opts in to `MaterialExpressiveTheme` and `MotionScheme.expressive()`, but only consumes about a third of what the alpha actually ships. Concrete swaps, in priority order:

### [x] 1.1 Adopt the new Expressive type scale (`Type.kt`)

`AppTypography` is a hand-rolled copy of the *2022* Material 3 type scale. M3 Expressive ships a *new* scale with extra emphasized roles. Replace `app/src/main/java/dev/bikram/remember/ui/theme/Type.kt` with the catalog provided by `androidx.compose.material3.Typography()` no-arg constructor (which now returns the Expressive scale on the alpha library), then layer your custom `displayLarge` / `headlineLarge` only where you actually want a deviation.

You will also pick up the new emphasized weights (`*Emphasized` styles - `bodyLargeEmphasized`, `titleMediumEmphasized`, etc.) which are the Expressive language's calling card. Use them on:
- The home screen's `LargeTopAppBar` title (currently `Text(stringResource(R.string.app_name))` - use `style = MaterialTheme.typography.headlineLargeEmphasized`).
- Section headers in `HomeScreen.GroupHeader` (line 1367).
- Sheet titles in `AppBottomSheet`.

### [x] 1.2 Switch from `RoundedCornerShape` to `MaterialTheme.shapes` everywhere

73 raw `RoundedCornerShape(...)` call sites. M3 Expressive defines a 7-step shape token system - extra-small through extra-extra-large - exposed via `MaterialTheme.shapes`. Swap the constants at `NoteCard.kt:62`, `SwipeableRememberNoteCard.kt:33`, `BottomSheet.kt`, the action bars in `HomeScreen`, `EditNoteScreen`, etc. to:

```kotlin
private val NoteCardShape get() = MaterialTheme.shapes.large    // instead of RoundedCornerShape(12.dp)
```

Then *override* `Shapes` in your theme so a single edit changes the whole app's silhouette. Today every screen has a slightly different corner radius (`14.dp`, `16.dp`, `20.dp`, `24.dp`, `28.dp`, `999.dp`) - this is exactly the inconsistency the shape token system was created to remove.

### [x] 1.3 Use `MaterialShapes` and shape morphing on the FAB and selection-mode badges

This is the single biggest visual upgrade available to you. M3 Expressive ships `MaterialShapes` (cookie, clover, pill, sunny, oval, gem, bun, etc.) plus `Morph` which animates between any two `RoundedPolygon` shapes. You're a notes app - this is your moment.

- **FAB**: `MainTabScaffold.kt:264` builds a circular FAB that swaps icons between `add` and `close` when the speed dial opens. Replace with a `Morph`-animated FAB whose silhouette transitions from `MaterialShapes.Cookie9Sided` (idle) to `MaterialShapes.Sunny` (expanded). Reference: `androidx.compose.material3.MaterialShapes` + `androidx.graphics.shapes.Morph` + `Modifier.clip(MorphShape(progress))`.
- **Selection check badge**: `NoteCard.kt:264` is a hard `CircleShape`. Use `MaterialShapes.Cookie7Sided` so multi-select feels delightful instead of utilitarian.
- **Empty-state illustration backgrounds** in `EmptyNotesIllustration.kt` are perfect for slow-rotating star/clover shapes.
- **Reminder/recurrence dots**: pin `RoundedPolygon`-based small icons instead of the current circular dots.

### [x] 1.4 Replace hand-built progress visuals with `LoadingIndicator` / `ContainedLoadingIndicator`

You already discovered `LoadingIndicator` in `GoogleTasksImportScreen.kt:476`. The same expressive blob-morph indicator should replace:
- `CircularProgressIndicator` in `SettingsScreen.kt:59` (backup-in-progress).
- `LinearProgressIndicator` in `SettingsScreen.kt:66` (used for play in-app update progress).
- Any custom spinner in onboarding.

`ContainedLoadingIndicator` for any in-button progress states (saving a note in `EditNoteScreen` could use a button-contained version when backups are syncing).

### [x] 1.5 `SplitButton` for the import flow

`GoogleTasksImportScreen.ImportButtonGroup` (line 1198) reinvents the segmented split-action button. Material 3 Expressive ships `SplitButton` (`androidx.compose.material3.SplitButton`) - a pill that combines a primary action with a chevron menu. Drop in the official component and delete ~40 lines of custom code.

### [x] 1.6 `ButtonGroup` instead of hand-spaced rows

`AppearanceSection.kt:80` already uses `ButtonGroupDefaults.ConnectedSpaceBetween`, but the rest of the app builds toggles with `Row + Spacer + IconButton` (e.g. `HomeSelectionActionBar` at `HomeScreen.kt:1230`). Wrap that bar in `ButtonGroup` and the selection action cluster gains the rubber-band squeeze and over-press effects for free.

### [x] 1.7 `WideNavigationRail` + `NavigationSuiteScaffold` for tablets and foldables

minSdk is 30, which means foldables. `MainTabScaffold` is locked to a phone-only floating bottom toolbar. Wrap it with:

```kotlin
NavigationSuiteScaffold(
    layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
        currentWindowAdaptiveInfo()
    ),
    navigationSuiteItems = { /* ... */ }
) { /* current scaffold body */ }
```

On phones you keep the floating toolbar; on a Pixel Fold's outer or inner display the same code becomes a `WideNavigationRail`. This is a one-day change with outsized payoff.

### [x] 1.8 Adopt `Modifier.predictiveBack` (Android 14+)

minSdk 30 means most active devices support predictive back. The bottom sheets, full-screen image overlay (`FullScreenHeroImageOverlay`), and edit screen should declare predictive-back support so the system shows the swipe-from-edge preview. The Compose API is `Modifier.predictiveBackProgress` + `BackHandler` paired with `predictive_back` xml.

In the manifest, set `android:enableOnBackInvokedCallback="true"` on `<application>` (it is currently absent). That alone unlocks predictive back system animations across all your back navigation.

---

## [x] 2. Motion and animation polish

### [x] 2.1 Replace ad-hoc `tween(300)` with motion tokens

`SwipeableRememberNoteCard.kt:108` uses `tween(300)`. The whole point of `MotionScheme.expressive()` is that you read motion specs from the scheme:

```kotlin
val backgroundColor by animateColorAsState(
    targetValue = action.semanticSwipeBackground(),
    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    label = "swipeBg",
)
```

Same for the chevron rotation in `HomeScreen.GroupHeader:1385` (already correct - use it as the template), and the card border color animation at `SettingsScreen` etc. Search for every `tween(` and `spring(` outside `MotionScheme` and convert them. Today there are 17 raw `graphicsLayer` blocks and dozens of `animate*AsState` calls with bespoke specs.

### [x] 2.2 Container transform for note open

You already provide `LocalSharedTransitionScope` and `Modifier.sharedBounds(...)` for the card-to-editor transition (`NoteCard.kt:84-93` and `EditNoteScreen.kt:75-87`). Two upgrades:

1. Add `sharedElement` (not just `sharedBounds`) on the *title* and *icon* inside the card so they fly into their positions on the editor's `LargeTopAppBar` - container transforms feel cinematic only when the *contents* track too.
2. Use `BoundsTransform` with `MaterialTheme.motionScheme.defaultSpatialSpec()` so the morph takes the Expressive cubic-bezier instead of the default spring.

### [x] 2.3 Stagger the LazyColumn on first appear

`HomeScreen.kt:1102` uses `Modifier.animateItem(placementSpec = ...slowSpatialSpec())` for *placement* changes, but new items pop in with no entrance. With M3E you can pass `fadeInSpec` and `placementSpec` separately:

```kotlin
modifier = Modifier.animateItem(
    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    placementSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
),
```

Combined with a tiny `LaunchedEffect` that triggers `listState.scrollToItem(0, scrollOffset = -16)` on first composition, the home screen will *unfurl* on launch instead of just appearing.

### [x] 2.4 Add `FilterChipDefaults` ripple + selection morph to the active filter chips

`ActiveFilterChips.kt` should use `InputChip` with a leading avatar slot for the tag color (you already have `tagRepository.observeTagColorMap()` - feed the tag's color as a 12.dp circle into the chip's `leadingIcon`). The chip's `selected` state will animate the M3E shape morph automatically.

### [x] 2.5 Replace the speed-dial overlay with the official `ToggleFloatingActionButton` API

`MainTabScaffold.SpeedDialOverlay` is a hand-built FAB menu. M3 Expressive ships `ToggleFloatingActionButton` + `FloatingActionButtonMenu` precisely for this. The official one already handles:
- Predictive-back collapse animation
- Backdrop scrim
- Springy stagger on the menu items
- Accessibility focus order

That deletes ~70 lines of `MainTabScaffold.kt` and gets you better behavior than the current implementation.

---

## 3. Code-architecture improvements

### [x] 3.1 Break up the giants

| File | Lines | Recommendation |
|------|-------|----------------|
| `ui/settings/SettingsScreen.kt` | 2969 | Split per section: `BackupSection`, `RemindersSection`, `LockSection`, `AboutSection`. Each section as its own file. The `AppearanceSection.kt` file is already the model. |
| `ui/home/HomeScreen.kt` | 1725 | Extract `HomeTopBar`, `HomeSelectionActionBar`, `HomeEmptyState`, `SearchSectionPillDivider`, `GroupHeader` into `ui/home/parts/`. The `HomeScreen` composable should be ~200 lines. |
| `ui/edit/EditNoteScreen.kt` | 567 | Reasonable, but the picker block (lines 452-545) deserves a `EditNotePickers.kt` companion. |
| `googletasks/GoogleTasksImportScreen.kt` | 1300+ | Heavy - extract the `ImportButtonGroup`, status banner, and progress widget. |

These files are slow to recompose because *every* `state` change in the parent invalidates the whole file's composables. Splitting also lets `@Stable` and `@Immutable` annotations actually do their job.

### 3.2 ViewModel boundary leaks

`HomeScreen` currently passes the raw `NoteRepository` from `MainTabScaffold` into `HomeRoute`, then `HomeRoute` *also* injects a `HomeViewModel`. The repository should live entirely behind the ViewModel - no UI composable should know what a repository is. Same in `EditNoteRoute`. Concrete fix: move the swipe-action effects (`HomeRoute` lines 724-744 - `repository.moveToTrash`, `repository.duplicateNote`, etc.) into the ViewModel so the route only forwards `(note, action) -> Unit`.

### 3.3 Save with `@Immutable` on UI state

`HomeState`, `NotesFilter`, `ViewOptions`, `InteractionState` are all data classes with `List<...>` properties. Compose treats those as Unstable by default, which forces unnecessary recomposition. Add `@Immutable` to each, and wrap the lists in `kotlinx.collections.immutable.persistentListOf` (`org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0`). This visibly reduces recompositions on the home screen.

### [x] 3.4 Use `Flow` operators on the IO dispatcher

`NoteRepository.observeActive()` returns the raw Room flow. Add `.flowOn(Dispatchers.IO)` once at the repository level so every consumer in every ViewModel doesn't have to think about it. There are no `flowOn` calls anywhere in the codebase right now, which means main-thread mapping is happening for `searchNotes`, `searchArchived`, etc. when the Room result lands.

### 3.5 Hilt-injected `CoroutineDispatcher`s

`NoteRepository` constructs `clock: () -> Long = System::currentTimeMillis`. Good pattern. Apply it to dispatchers too: inject `@IoDispatcher` and `@DefaultDispatcher` so Robolectric tests can swap them. Right now testing anything that touches the repository requires running on a real test dispatcher.

### 3.6 Replace `JSONArray/JSONObject` typeconverters with kotlinx-serialization

`Converters` in `RememberDatabase.kt` uses `org.json.JSONArray` for `List<NoteAction>`, `List<String>`, etc. You already depend on `kotlinx-serialization-json`. Switching saves the 70-line `fromActions/toActions` boilerplate, gives you compile-time type safety, and is a real perf win on large lists. Pair with Room's `@TypeConverter` over a single `Json` instance.

### 3.7 Move `iconKey -> drawable/symbol/emoji` lookup into a sealed type

`NoteCard.kt:158-203` cascades through three nullable lookups (`iconSymbolName`, `iconDrawableRes`, `iconEmojiPayload`) plus two `kind`-fallbacks. Replace with:

```kotlin
sealed class NoteIcon {
    data class Symbol(val name: String): NoteIcon()
    data class Drawable(@DrawableRes val res: Int): NoteIcon()
    data class Emoji(val text: String): NoteIcon()
    data object NotePlaceholder: NoteIcon()
    data object ListPlaceholder: NoteIcon()
}

fun resolveNoteIcon(iconKey: String?, kind: NoteKind): NoteIcon = ...
```

Then a single `when` in `NoteCard` instead of three `if-else if` chains.

---

## 4. Performance

### 4.1 Baseline profile coverage is thin

`baselineprofile/src/main/...` exists but hand it the actual user journeys. Add scenarios for:
- Cold launch -> Home (already there?)
- Open a note (sharedBounds path)
- Edit a note + save
- Open settings -> appearance section
- Speed-dial -> create list
- Apply tag filter

Each scenario reduces TTI on the next install by ~10-30 ms.

### 4.2 Compose-compiler stability report

Add `-Pkotlin.compiler.metrics.destination=$buildDir/compose_metrics` to the kotlin compose plugin in `app/build.gradle.kts`. Running `./gradlew :app:assembleRelease -Pkotlin.compiler.metrics.destination=...` produces a Markdown report listing every Unstable parameter in your composables. You'll find recompose hot-spots in 5 minutes.

### 4.3 LazyColumn `key` strategies

`HomeScreen.kt:1046-1050` stringifies keys ("h:..." / "n:..."). Compose `LazyListScope.items` accepts `Long` keys directly (which is what you have - `note.note.id`). Strings allocate; longs don't. Two changes:
- Header keys: hash the `stableKey` string at compose time, or store an enum stable key.
- Note row keys: pass `note.note.id` directly when there's no group disambiguation.

### 4.4 `derivedStateOf` for `selectableVisibleIds`

`HomeScreen.kt:811` recomputes `selectableVisibleIds` whenever `displayedItems` recomposes. Wrap in `remember { derivedStateOf { ... } }` so it only re-derives when `displayedItems` actually changes by structural equality.

### 4.5 Drop the `View.playSoundEffect` polling in `Theme.kt`

`Theme.kt:89-101` builds a `LongArray` and reads `SystemClock.uptimeMillis()` on every tap. This is fine, but you can move the rate-limiter into a `class TapSoundPlayer(view: View)` allocated once via `remember` instead of recomposing the lambda. Also: per your CLAUDE.md guidance, prefer not to reach for `SystemClock` if `view.handler.looper` already gives you frame timing.

### 4.6 Glance widget is doing `repository.observeActive().first()`

`NotesWidget.kt:64` collects the *active flow* once, but then calls `reminderSummaryItems` separately. Use `combine` so they're both fetched in parallel rather than sequentially. Glance widgets are time-budgeted; a 50 ms saving per provideGlance call is meaningful.

---

## 5. Localization, accessibility, polish

### 5.1 Hard-coded strings outside `strings.xml`

Per your CLAUDE.md, all user-facing strings must live in `strings.xml`. The audit caught zero string literals inside `Text(...)` calls in the UI tree (excellent), but check:
- `Toast.makeText(context, "...", ...)` - `EditNoteScreen.kt:271-275` uses `changesSavedMsg = stringResource(...)` already. Good. Confirm none slipped through in `SettingsScreen.kt`.
- `applicationId` and the default seed color name "Default" if it's surfaced.
- Speed-dial intent extras and accessibility labels for the widget are in `widget_*` strings - confirmed good.

Also: there are no `values-*/` directories. If you ever consider translation, add `<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">` to flag untranslated strings, and create a `locales_config.xml` referenced from the manifest's `android:localeConfig` attribute (per-app language selection on Android 13+).

### 5.2 Accessibility

- `EmptyNotesIllustration` has no `contentDescription` - decorative is fine but the shape *and* the text together should be a single semantics merge. Wrap in `Modifier.semantics(mergeDescendants = true)`.
- `NoteCard` is a tap target. Make sure the *whole card* is announced as one button - it currently has separate semantics nodes for the favorite icon (`contentDescription = favoriteIconDescription`), the picture icon, the attachment icon, the reminder text. TalkBack will read each in sequence. The right pattern is `Modifier.semantics(mergeDescendants = true) { contentDescription = "Note: $title, $previewBody, due $reminder, tagged $tags" }` and clear the children's descriptions.
- The `HorizontalFloatingToolbar` nav bar uses `contentDescription = fabDesc` on the FAB (good) but the tab `IconButton` calls have no `contentDescription` set explicitly - the text label inside is the description, which works only when the label is visible (selected tab). Inactive tabs are unlabeled to TalkBack right now. Fix: set `contentDescription = stringResource(item.labelRes)` on each `RememberIconButton`.

### 5.3 Dynamic type and `BasicTextField` in the search field

`HomeScreen.InlineSearchField` (line 870) uses `BasicTextField` directly. The new M3E `SearchBar` / `DockedSearchBar` provides:
- Built-in voice input affordance
- Predictive search expansion
- IME insets handled correctly
- Keyboard-aware scrim

Swap to `DockedSearchBar` and remove the manual `AnimatedContent` wiring for the title slot.

### 5.4 Light theme audit

`Theme.kt` does the right thing for status bar appearance, but the gradient background (`GradientBackground`, line 137) uses `primaryContainer.copy(alpha = 0.45f)` over `surface`. On `ThemeMode.BLACK` the surface is `#000000`; the gradient becomes invisible. Currently the code reads `themeState.useGradient`, which the user can disable - good - but the *automatic* behavior in BLACK mode should be "skip gradient regardless of preference" so the AMOLED savings aren't undermined.

### 5.5 Edge-to-edge tweaks

`MainActivity.onCreate` calls `enableEdgeToEdge()`. Excellent. For Android 15+ (API 35) this is enforced by default, but you should also pass an explicit `SystemBarStyle.auto(...)` so the *navigation* bar gets the same dark/light treatment as your status bar. Right now `Theme.kt:84` flips `isAppearanceLightNavigationBars` correctly, but the system gesture inset color is the platform default (translucent). Pass `SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }` to `enableEdgeToEdge()`.

---

## 6. Build / dev infra

### 6.1 The `composeBom` and `composeMaterial3Expressive` are pinned separately

`libs.versions.toml`:
```toml
composeBom = "2026.04.01"
composeMaterial3Expressive = "1.5.0-alpha18"
```

This is correct *today* - the BOM ships stable Material3 and you override it for the alpha. But `material3` 1.5 has shipped the new `MaterialShapes` and `Morph` APIs, plus `ToggleFloatingActionButton`, `FloatingActionButtonMenu`, `WideNavigationRail`. Most of section 1's recommendations are *available* on alpha18 - confirm with the API doc before relying on the names.

### 6.2 Add the `androidx.graphics:graphics-shapes` dependency

For the `MaterialShapes`/`RoundedPolygon`/`Morph` work in 1.3, you'll want `androidx.graphics:graphics-shapes:1.0.1` directly so you can build your *own* shapes (e.g. a custom Remember logo polygon for the empty state).

### 6.3 ProGuard rules

`app/proguard-rules.pro` should keep the `com.materialkolor.*` data classes that drive `material-kolor` palette generation - if R8 strips reflection it will work in debug and crash in `devRelease`. Easy to verify with `./gradlew :app:assembleDevRelease && adb install ...`.

### 6.4 Detekt config

`buildUponDefaultConfig = true` is on. Add `complexity:LongMethod` with `threshold = 80` and `complexity:LargeClass` with `threshold = 600` and the splits in 3.1 will become enforced rather than aspirational.

### 6.5 Lint baseline

There is no `lint-baseline.xml`. Run `./gradlew :app:lintGithubRelease`, capture and review the output, then `./gradlew :app:updateLintBaseline` to commit a baseline. Future regressions become CI-blocking.

### 6.6 Secrets / signing

`build.gradle.kts:35-83` falls back to "no signingConfig if keystore.properties absent". CI workflow should write `keystore.properties` from `${{ secrets.KEYSTORE_PROPS_BASE64 }}` before the assemble step. The current code is well-structured for that.

---

## 7. The next big delights to ship

If you want this app to feel *unmistakably* 2026 Android:

1. **Live wallpaper-aware tinting** - already partly there (Material You). Now add `WallpaperManager.getDrawable()` in the gradient background so the page feels rooted in the user's home screen.
2. **Notification-style note previews** in the widget using `Notifications.Decorator` - or simply migrate the widget to the new `RemoteViews + Compose` interop in Glance 1.2 (you're on 1.1.1; 1.2 brings expressive components to Glance).
3. **Keyboard / IME chrome for the editor** - the bottom format bar should be the system input method's "stylus assist" panel on tablets when an S-Pen is detected (`MotionEvent.TOOL_TYPE_STYLUS`).
4. **Live tag pill morph** - when the user types `#work` in a note body, the text should morph into an actual tag chip via `Morph` between the text-glyph bounds and the chip shape. This is the kind of micro-delight the Expressive language was designed for.
5. **Reduced motion respect** - `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` means the user has motion disabled. Provide a `LocalReducedMotion` composition local that swaps spring specs for `tween(0)`. Currently `MotionScheme.expressive()` ignores this user preference.

---

## Quick-win priority list

If picking just five things to ship in the next release:

1. **Adopt `MaterialTheme.shapes`** project-wide (1.2). One PR, app-wide consistency win.
2. **Switch FAB to `ToggleFloatingActionButton` + `FloatingActionButtonMenu`** (2.5). Deletes code, adds polish.
3. **Move to Expressive type scale and `*Emphasized` styles** (1.1).
4. **Add `flowOn(Dispatchers.IO)` in `NoteRepository`** (3.4). Performance, ~free.
5. **Split `SettingsScreen.kt` and `HomeScreen.kt`** (3.1). Faster recompose, easier review.

Three more for when you have the appetite:

6. **`MaterialShapes` morph on FAB and selection badge** (1.3).
7. **`NavigationSuiteScaffold`** for foldables (1.7).
8. **Predictive back end-to-end** (1.8).
