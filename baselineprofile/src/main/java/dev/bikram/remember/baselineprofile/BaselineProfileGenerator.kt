package dev.bikram.remember.baselineprofile

import android.content.Context
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        // The instrumentation's target context for a baseline-profile test is the test
        // module itself (its packaged manifest only declares targetPackage =
        // "dev.bikram.remember.baselineprofile"), so we cannot use its packageName as the
        // app under test. Resolve the app's installed package by probing candidates and
        // looking for one whose resources contain the app's strings.
        val testContext =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val (resourceContext, targetPackageName) = ProfileLabels.resolveAppResources(testContext)
        val labels = ProfileLabels.from(resourceContext, targetPackageName)

        baselineProfileRule.collect(packageName = targetPackageName) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            device.dismissStartupPrompts(labels)

            // Every step now begins by parking the app on the Notes tab root, so a step that
            // misfires cannot poison the next step. The previous version leaked screen state
            // between steps (e.g. left the Tags dropdown open or sat on Appearance) which
            // turned later steps into no-ops or, worse, into stray pressBack() calls that
            // exited the app to the launcher.
            device.returnToNotesRoot(labels)

            device.scrollActiveContent()
            device.createNoteForProfile(labels)
            device.returnToNotesRoot(labels)

            device.openProfileNote(labels)
            device.editOpenNoteAndSave(labels)
            device.returnToNotesRoot(labels)

            device.openSettingsAppearance(labels)
            device.returnToNotesRoot(labels)

            device.createListFromSpeedDial(labels)
            device.returnToNotesRoot(labels)

            device.exerciseTagFilterDropdown(labels)
            device.returnToNotesRoot(labels)
        }
    }

    private fun UiDevice.dismissStartupPrompts(labels: ProfileLabels) {
        // Onboarding can be up to a few full-screen steps; loop until none of the known
        // buttons are visible any more rather than capping at three reps.
        repeat(MAX_ONBOARDING_STEPS) {
            val handled =
                clickTextIfVisible(labels.onboardingBegin) ||
                    clickTextIfVisible(labels.onboardingSkip) ||
                    clickTextIfVisible(labels.onboardingContinue)
            if (!handled) return
            waitForIdle()
        }
    }

    private fun UiDevice.scrollActiveContent() {
        runCatching {
            val scrollableContent = UiScrollable(UiSelector().scrollable(true))
            if (scrollableContent.exists()) {
                scrollableContent.flingToEnd(3)
                scrollableContent.flingToBeginning(3)
            }
        }
    }

    private fun UiDevice.createNoteForProfile(labels: ProfileLabels) {
        runCatching {
            // Empty state shows a "Create note" button on the very first iteration. Once any
            // note exists, that button is gone and we have to fall through to the speed dial
            // on the Notes-tab FAB.
            if (!clickTextIfVisible(labels.homeCreateNote)) {
                openNotesSpeedDial(labels)
                clickTextIfVisible(labels.speedDialNote)
            }
            waitForIdle()
            enterFocusedText(PROFILE_NOTE_TITLE)
            clickDescriptionIfVisible(labels.editSaveDescription)
            // Save only flips the editor to view mode; one back press closes the editor and
            // lands us on the Notes tab. safePressBack() is a no-op if a flake somehow
            // already dropped us out of the app.
            safePressBack()
        }
    }

    private fun UiDevice.openProfileNote(labels: ProfileLabels) {
        runCatching {
            if (!clickTextIfVisible(PROFILE_NOTE_TITLE)) {
                clickTextIfVisible(labels.untitledNote)
            }
            waitForIdle()
        }
    }

    private fun UiDevice.editOpenNoteAndSave(labels: ProfileLabels) {
        runCatching {
            // Existing notes open in view mode; flip to edit, type, save back to view mode,
            // then back out to the Notes tab.
            if (!clickTextIfVisible(labels.edit)) return@runCatching
            waitForIdle()
            enterFocusedText(PROFILE_NOTE_APPEND_TEXT)
            clickDescriptionIfVisible(labels.editSaveDescription)
            safePressBack()
        }
    }

    private fun UiDevice.openSettingsAppearance(labels: ProfileLabels) {
        runCatching {
            if (!clickTextIfVisible(labels.settingsTab)) return@runCatching
            waitForIdle()
            scrollTextIntoView(labels.appearance)
            clickTextIfVisible(labels.appearance)
            waitForIdle()
        }
    }

    private fun UiDevice.createListFromSpeedDial(labels: ProfileLabels) {
        runCatching {
            openNotesSpeedDial(labels)
            if (!clickTextIfVisible(labels.speedDialChecklist)) return@runCatching
            waitForIdle()
            enterFocusedText(PROFILE_LIST_TITLE)
            clickDescriptionIfVisible(labels.editSaveDescription)
            safePressBack()
        }
    }

    private fun UiDevice.exerciseTagFilterDropdown(labels: ProfileLabels) {
        runCatching {
            // The previous version tried to click the "Untagged" home-section header inside
            // the Tags dropdown menu. That string never appears in the dropdown (the dropdown
            // shows real tag names), so the click silently no-op'd and the trailing
            // pressBack() exited the app to the launcher. Just open the dropdown and let the
            // dropdown's own back handler dismiss it; that exercises the same code paths
            // without leaving the Notes tab.
            if (!clickTextIfVisible(labels.tags)) return@runCatching
            waitForIdle()
            // Compose's DropdownMenu installs its own BackHandler that closes the menu when
            // back is pressed, so this back press dismisses the dropdown rather than the
            // activity. safePressBack() additionally ensures we don't escape the app if the
            // dropdown's back handler somehow didn't register in time.
            safePressBack()
        }
    }

    /**
     * Bring the app back to the Notes-tab root without ever pressing back from the root.
     *
     * MainTabScaffold's back handler exits the app when the user is on the Notes tab with
     * nothing open, so a stray pressBack() at root drops to the launcher and breaks the rest
     * of the iteration. This helper closes a few known overlays (FAB speed dial, edit
     * screens) by clicking nav targets directly instead of pressing back.
     *
     * Lookups here use [findObject] (no waiting) instead of [clickDescriptionIfVisible] /
     * [clickTextIfVisible] because the common case is "nothing to close, already on Notes
     * tab" - paying [OBJECT_WAIT_TIMEOUT_MS] for each absent element on every step (we call
     * this 7x per iteration) was burning ~35 seconds per iteration just spinning.
     */
    private fun UiDevice.returnToNotesRoot(labels: ProfileLabels) {
        runCatching {
            // If the FAB speed dial is open, its content description switches from "Create"
            // to "Close"; one tap collapses it. Don't wait for it - if it's not there now,
            // it isn't going to appear.
            findObject(By.desc(labels.fabClose))?.let {
                it.click()
                waitForIdle()
            }
            // Tap the Notes tab if we can see its label. From a non-Notes tab this switches
            // tabs; from the Notes tab it's effectively a no-op. From a sub-screen with the
            // bottom nav hidden, the lookup just returns null and we fall through.
            findObject(By.text(labels.notesTab))?.let {
                it.click()
                waitForIdle()
            }
        }
    }

    /**
     * Press back only if the foreground app is the app under test. This guards against the
     * case where a previous step accidentally exited the app to the launcher; without the
     * guard, every subsequent pressBack() in the iteration would either hit the launcher's
     * own handlers or just sit unhandled, and follow-on text/desc lookups would search the
     * launcher's UI tree instead of the app's.
     */
    private fun UiDevice.safePressBack() {
        val current = currentPackageName
        if (current != null && current.startsWith(BASE_RESOURCE_PACKAGE)) {
            pressBack()
            waitForIdle()
        }
    }

    private fun UiDevice.openNotesSpeedDial(labels: ProfileLabels) {
        if (!clickDescriptionIfVisible(labels.fabCreate)) {
            clickTextIfVisible(labels.fabCreate)
        }
        waitForIdle()
    }

    private fun UiDevice.clickTextIfVisible(text: String): Boolean {
        wait(Until.hasObject(By.text(text)), OBJECT_WAIT_TIMEOUT_MS)
        val visibleObject = findObject(By.text(text)) ?: return false
        visibleObject.click()
        waitForIdle()
        return true
    }

    private fun UiDevice.clickDescriptionIfVisible(description: String): Boolean {
        wait(Until.hasObject(By.desc(description)), OBJECT_WAIT_TIMEOUT_MS)
        val visibleObject = findObject(By.desc(description)) ?: return false
        visibleObject.click()
        waitForIdle()
        return true
    }

    private fun UiDevice.scrollTextIntoView(text: String) {
        runCatching {
            val scrollableContent = UiScrollable(UiSelector().scrollable(true))
            if (scrollableContent.exists()) {
                scrollableContent.scrollTextIntoView(text)
            }
        }
    }

    private fun UiDevice.enterFocusedText(text: String) {
        // Two races to defuse here:
        //   1. After tapping into an editor screen, the focused TextField hasn't actually
        //      received focus yet by the time waitForIdle() returns. waitForIdle only blocks
        //      until the UI thread quiesces; it does not guarantee that focus has been
        //      delivered or that an InputConnection has been wired up.
        //   2. executeShellCommand("input text ...") synthesizes keyboard events at the
        //      InputManager level. Events sent before the field is ready are silently
        //      dropped, which is what produced "ProfileList" arriving as "ileList".
        //
        // The earlier attempt to bypass shell input via UiObject2.text = ... (accessibility
        // ACTION_SET_TEXT) failed because By.focused(true) can briefly match a focusable-
        // but-not-editable element during the navigation animation (icon button, back
        // button, the speed-dial item that was just clicked) and ACTION_SET_TEXT silently
        // no-ops on those, leaving the title empty.
        //
        // So: wait for *some* focused node, give the focus animation a beat to land on the
        // actual TextField, then inject via shell input. The brief sleep is unfortunate but
        // there is no synchronization signal we can wait on for "InputConnection ready" -
        // accessibility focus and IME focus are independent.
        //
        // executeShellCommand("input text ...") splits on whitespace, so the strings below
        // must be single tokens. The PROFILE_* constants are deliberately single tokens.
        waitForIdle()
        wait(Until.hasObject(By.focused(true)), FOCUSED_FIELD_WAIT_TIMEOUT_MS)
        waitForIdle()
        Thread.sleep(POST_FOCUS_SETTLE_MS)
        executeShellCommand("input text $text")
        waitForIdle()
    }

    private data class ProfileLabels(
        val onboardingBegin: String,
        val onboardingSkip: String,
        val onboardingContinue: String,
        val homeCreateNote: String,
        val fabCreate: String,
        val fabClose: String,
        val speedDialNote: String,
        val speedDialChecklist: String,
        val notesTab: String,
        val settingsTab: String,
        val untitledNote: String,
        val edit: String,
        val editSaveDescription: String,
        val appearance: String,
        val tags: String,
    ) {
        companion object {
            /**
             * Resolve [ProfileLabels] from a context whose resources actually contain the
             * app's string table. Pair this with [resolveAppResources] to discover the
             * (context, package) pair, since the baseline-profile test process otherwise
             * has no access to the app's resources at all.
             */
            fun from(resourceContext: Context, resourcePackage: String): ProfileLabels {
                fun stringFor(resourceName: String): String {
                    val resourceId =
                        resourceContext.resources.getIdentifier(
                            resourceName,
                            "string",
                            resourcePackage,
                        )
                    check(resourceId != 0) {
                        "Missing string resource: $resourceName (looked up in $resourcePackage)"
                    }
                    return resourceContext.getString(resourceId)
                }

                return ProfileLabels(
                    onboardingBegin = stringFor("onboarding_lets_begin"),
                    onboardingSkip = stringFor("onboarding_permissions_skip_for_now"),
                    onboardingContinue = stringFor("onboarding_permissions_continue"),
                    homeCreateNote = stringFor("home_create_note"),
                    fabCreate = stringFor("main_fab_create"),
                    fabClose = stringFor("main_fab_close"),
                    speedDialNote = stringFor("main_speed_dial_note"),
                    speedDialChecklist = stringFor("main_speed_dial_checklist"),
                    notesTab = stringFor("main_tab_notes"),
                    settingsTab = stringFor("main_tab_settings"),
                    untitledNote = stringFor("edit_note_title_new"),
                    edit = stringFor("edit_bottom_bar_edit"),
                    editSaveDescription = stringFor("edit_save_cd"),
                    appearance = stringFor("settings_section_appearance"),
                    tags = stringFor("filter_dropdown_tags"),
                )
            }

            /**
             * Find a [Context] whose resources contain the app's string table, paired with
             * the package name to use for [Resources.getIdentifier] lookups.
             *
             * Strategy:
             * 1. Try [testContext]'s own reported package (covers the rare case where the
             *    test instrumentation actually targets the app).
             * 2. Try the base namespace and the known applicationId suffixes (".gh" for the
             *    github flavor, ".dev" for the devRelease build type).
             *
             * For each candidate we call [Context.createPackageContext], which only succeeds
             * if that package is installed on the device. We probe with one sentinel string
             * resource to make sure we landed on a context with the app's resources rather
             * than, say, the test module itself (which has none of them).
             */
            fun resolveAppResources(testContext: Context): Pair<Context, String> {
                // applicationIdSuffix from the flavor and build type concatenate, so the
                // github + devRelease variant lands on "dev.bikram.remember.gh.dev". List
                // every combination so any variant the developer runs against just works.
                val candidatePackages =
                    linkedSetOf(
                        testContext.packageName,
                        BASE_RESOURCE_PACKAGE,
                        "$BASE_RESOURCE_PACKAGE$GITHUB_SUFFIX",
                        "$BASE_RESOURCE_PACKAGE$DEV_SUFFIX",
                        "$BASE_RESOURCE_PACKAGE$GITHUB_SUFFIX$DEV_SUFFIX",
                    )
                val resolved =
                    candidatePackages.firstNotNullOfOrNull { pkg ->
                        val ctx =
                            runCatching { testContext.createPackageContext(pkg, 0) }
                                .getOrNull()
                                ?: return@firstNotNullOfOrNull null
                        val sentinel =
                            ctx.resources.getIdentifier(SENTINEL_RESOURCE, "string", pkg)
                        if (sentinel == 0) null else ctx to pkg
                    }
                return resolved
                    ?: error(
                        "Could not locate the target app's resources. Tried: $candidatePackages. " +
                            "If the app's namespace or applicationIdSuffix changed, update " +
                            "BASE_RESOURCE_PACKAGE / GITHUB_SUFFIX / DEV_SUFFIX.",
                    )
            }
        }
    }

    private companion object {
        // 1s was too tight for cold-start animations and tab transitions: lookups silently
        // timed out, runCatching swallowed the misses, and the next step ran on the wrong
        // screen, which is what produced the "drops to homescreen / repeats edit-note"
        // symptom. 5s is the standard baseline-profile UiAutomator wait.
        private const val OBJECT_WAIT_TIMEOUT_MS = 5_000L
        // Generous timeout for "is anything focused yet". Longer than OBJECT_WAIT_TIMEOUT_MS
        // because cold-start + screen-navigation animations on slower emulators can take a
        // surprising amount of time to deliver focus to a freshly-mounted TextField.
        private const val FOCUSED_FIELD_WAIT_TIMEOUT_MS = 5_000L

        // Sleep after focus is reported but before injecting text via shell input. Even
        // after By.focused(true) sees a focused node, the IME's InputConnection may not be
        // wired up; events sent in that window are silently dropped, which is what produced
        // "ProfileList" arriving as "ileList". 350ms is a compromise: comfortable margin on
        // a slow emulator without dwarfing the rest of the test.
        private const val POST_FOCUS_SETTLE_MS = 350L
        private const val MAX_ONBOARDING_STEPS = 6
        private const val PROFILE_NOTE_TITLE = "ProfileNote"
        private const val PROFILE_NOTE_APPEND_TEXT = "Updated"
        private const val PROFILE_LIST_TITLE = "ProfileList"

        // App's namespace, where its string resources are registered. Stable across flavors
        // (github / playstore) and build types (release / devRelease), unlike applicationId
        // which gets ".gh" or ".dev" suffixes.
        private const val BASE_RESOURCE_PACKAGE = "dev.bikram.remember"
        private const val GITHUB_SUFFIX = ".gh"
        private const val DEV_SUFFIX = ".dev"

        // Probe string used to confirm a candidate package is the actual app. Picked
        // because it's a stable, app-only string that should never be present in stub /
        // test packages.
        private const val SENTINEL_RESOURCE = "onboarding_lets_begin"
    }
}
