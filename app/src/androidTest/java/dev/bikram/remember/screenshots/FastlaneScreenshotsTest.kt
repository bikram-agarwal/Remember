package dev.bikram.remember.screenshots

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.MainActivity
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.NoteLayoutMode
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.di.DevOptionsDependenciesEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end Fastlane screenshot generator for Remember (8 phone + 5 tablet + 2 tablet10 shots;
 * `9_widgets.jpg` is intentionally excluded).
 *
 * Skipped during regular `connectedAndroidTest` runs unless `-e captureScreenshots true` is
 * passed via instrumentation arguments (see root `capture_screenshots.ps1`).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FastlaneScreenshotsTest {
    @get:Rule(order = 0)
    val globalTimeout: Timeout = Timeout.seconds(60)

    @get:Rule(order = 1)
    val composeRule =
        createEmptyComposeRule(
            ComposeUiTestConfig(effectContext = UnconfinedTestDispatcher()),
        )

    private lateinit var targetContext: Context
    private lateinit var deps: DevOptionsDependenciesEntryPoint

    @Before
    fun setUp() {
        val shouldCapture =
            InstrumentationRegistry.getArguments().getString(ARG_CAPTURE_SCREENSHOTS) == "true"
        Assume.assumeTrue(
            "Skipping screenshot suite (pass -e $ARG_CAPTURE_SCREENSHOTS true to run)",
            shouldCapture,
        )
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        deps =
            EntryPointAccessors.fromApplication(
                targetContext.applicationContext,
                DevOptionsDependenciesEntryPoint::class.java,
            )
    }

    @Test
    fun seedDemoStateOnly() {
        runBlocking {
            ScreenshotDemoData.resetAndConfigureEnvironment(
                context = targetContext,
                deps = deps,
                themeConfig =
                    DemoThemeConfig(
                        themeMode = ThemeMode.LIGHT,
                        colorSource = ColorSource.DEFAULT,
                    ),
            )
            ScreenshotDemoData.seedFullDemoNotes(
                context = targetContext,
                noteRepository = deps.noteRepository(),
            )
        }
    }

    @Test
    fun phone1Home() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.DEFAULT,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            waitForText("Rhea Birthday")
            waitForText("Groceries")
            captureDeviceScreenshot("1_home.jpg")
        }
    }

    @Test
    fun phone2NotePage() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_EMBER,
                ),
            rheaStarred = true,
            openNoteSelector = { it.rheaBirthdayId },
        ) {
            waitForText("Rhea Birthday")
            waitForText("Call in the morning")
            waitForText("Call Rhea")
            captureDeviceScreenshot("2_note_page.jpg")
        }
    }

    @Test
    fun phone3Markdown() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_OCEAN,
                ),
            markdownIconKey = "symbol:data_object",
            openNoteSelector = { it.markdownShowcaseId },
        ) {
            waitForText("Markdown Showcase")
            waitForText("Multiline code", substring = true)
            composeRule.onNodeWithText("Multiline code", substring = true).performClick()
            waitForContentDescription("Switch to markdown code")
            captureDeviceScreenshot("3_markdown.jpg", clearFocusBeforeCapture = false)
        }
    }

    @Test
    fun phone4Checklist() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_GROVE,
                ),
            groceriesTag = "Errands",
            groceriesStarred = false,
            openNoteSelector = { it.groceriesId },
        ) {
            waitForText("Groceries")
            waitForText("Shaving kit", substring = true)
            composeRule.onNodeWithText("Shaving kit", substring = true).performClick()
            waitForText("Add item")
            captureDeviceScreenshot("4_checklist.jpg", clearFocusBeforeCapture = false)
        }
    }

    @Test
    fun phone5Icons() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.CURATED_GROVE,
                ),
            // The original shot shows the outlined `{}` icon; the picker opens scrolled to its
            // category ("Devices & tech") in outlined style.
            markdownIconKey = "symbol_outlined:data_object",
            openNoteSelector = { it.markdownShowcaseId },
        ) {
            waitForText("Markdown Showcase")
            composeRule.onAllNodesWithContentDescription("Change icon").onFirst().performClick()
            waitForText("Pick an icon")
            waitForText("Icons")
            captureDeviceScreenshot("5_icons.jpg")
        }
    }

    @Test
    fun phone6Actions() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_HONEY,
                ),
            markdownIconKey = "symbol:data_object",
            openNoteSelector = { it.markdownShowcaseId },
        ) {
            waitForText("Markdown Showcase")
            composeRule.onAllNodesWithText("Notification action").onFirst().performClick()
            waitForText("Call number")
            waitForText("Share content")
            captureDeviceScreenshot("6_actions.jpg")
        }
    }

    @Test
    fun phone7Reminders() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.CURATED_EMBER,
                ),
            openNoteSelector = { it.humbleBundleId },
        ) {
            waitForText("HumbleBundle Choice")
            openReminderSheetAndExpandSecondRepeat("Monthly on the last Tue")
            captureDeviceScreenshot("7_reminders.jpg")
        }
    }

    @Test
    fun phone8Settings() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.SYSTEM,
                    useBlackTheme = true,
                    colorSource = ColorSource.CURATED_GROVE,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            waitForContentDescription("Settings")
            composeRule.onNodeWithContentDescription("Settings").performClick()
            ensureAppearanceSectionVisible()
            waitForText("Use pure black dark theme")
            waitForText("Preview · Grove")
            captureDeviceScreenshot("8_settings.jpg")
        }
    }

    @Test
    fun tablet1Home() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.DEFAULT,
                    // Both panes in the app's colours (not the cover image's).
                    adaptiveNoteThemes = false,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            composeRule.onAllNodesWithText("Rhea Birthday").onFirst().performClick()
            waitForText("Call in the morning")
            waitForText("Call Rhea")
            captureDeviceScreenshot("tablet_1_home.jpg")
        }
    }

    @Test
    fun tablet2Actions() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_HONEY,
                    // Both panes in the app's colours (not the cover image's).
                    adaptiveNoteThemes = false,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            composeRule.onAllNodesWithText("Rhea Birthday").onFirst().performClick()
            waitForText("Call Rhea")
            // The tablet detail pane's hero image pushes the options panel below the fold.
            scrollIntoViewAndClick(composeRule.onAllNodesWithText("Notification action").onFirst())
            waitForText("Action type")
            waitForText("+11234567890")
            captureDeviceScreenshot("tablet_2_actions.jpg")
        }
    }

    @Test
    fun tablet3Icons() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.CURATED_GROVE,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Groceries")
            composeRule.onAllNodesWithText("Groceries").onFirst().performClick()
            waitForText("QFC Direction")
            composeRule.onAllNodesWithContentDescription("Change icon").onFirst().performClick()
            waitForText("Pick an icon")
            waitForText("Food & drinks")
            captureDeviceScreenshot("tablet_3_icons.jpg")
        }
    }

    @Test
    fun tablet4Reminders() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.CURATED_EMBER,
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            // HumbleBundle sits in the "Later" group, below the fold of the tablet list, so the
            // lazy list has not composed its card yet.
            scrollListToText("HumbleBundle Choice")
            composeRule.onAllNodesWithText("HumbleBundle Choice").onFirst().performClick()
            waitForText("Octopath Traveler II", substring = true)
            openReminderSheetAndExpandSecondRepeat("Monthly on the last Tue")
            captureDeviceScreenshot("tablet_4_reminders.jpg")
        }
    }

    @Test
    fun tablet5Settings() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.SYSTEM,
                    useBlackTheme = true,
                    colorSource = ColorSource.MATERIAL_YOU,
                    customSeeds = listOf("#78909C", "#EC407A"),
                ),
            markdownNoteTitle = "",
        ) {
            waitForText("Air Filter Replacement")
            val settingsByText =
                composeRule
                    .onAllNodesWithText("Settings")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (settingsByText.isNotEmpty()) {
                composeRule.onAllNodesWithText("Settings").onFirst().performClick()
            } else {
                composeRule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
            }
            ensureAppearanceSectionVisible()
            waitForText("Use pure black dark theme")
            waitForText("Preview · Material You")
            captureDeviceScreenshot("tablet_5_settings.jpg")
        }
    }

    @Test
    fun tablet10Home() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.LIGHT,
                    colorSource = ColorSource.DEFAULT,
                    noteLayoutMode = NoteLayoutMode.MOSAIC,
                ),
            includeTablet10Extras = true,
            markdownIconKey = "symbol:cake",
        ) {
            waitForText("Air Filter Replacement")
            waitForText("Epic Games")
            waitForText("Demo: Markdown")
            captureDeviceScreenshot("tablet10_1_home.jpg")
        }
    }

    @Test
    fun tablet10Reminders() {
        seedAndLaunch(
            themeConfig =
                DemoThemeConfig(
                    themeMode = ThemeMode.DARK,
                    colorSource = ColorSource.CURATED_EMBER,
                ),
            useEndDateOnSecondReminder = true,
            openNoteSelector = { it.humbleBundleId },
        ) {
            waitForText("HumbleBundle Choice")
            openReminderSheetAndExpandSecondRepeat("Monthly on the first Tue")
            captureDeviceScreenshot("tablet10_4_reminders.jpg")
        }
    }

    private fun ensureAppearanceSectionVisible() {
        composeRule.waitForIdle()
        SystemClock.sleep(300L)
        val alreadyExpanded =
            composeRule
                .onAllNodesWithText("Use pure black dark theme")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        if (!alreadyExpanded) {
            val appearanceHeader =
                composeRule
                    .onAllNodesWithText("Appearance")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
            if (appearanceHeader.isNotEmpty()) {
                composeRule.onAllNodesWithText("Appearance").onFirst().performClick()
                composeRule.waitForIdle()
            }
        }
    }

    /** Scrolls the node's enclosing scroll container (if any) so the tap lands on-screen. */
    private fun scrollIntoViewAndClick(node: SemanticsNodeInteraction) {
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    /** Scrolls whichever lazy container holds [text] until its item is composed and visible. */
    private fun scrollListToText(text: String) {
        val scrollables = composeRule.onAllNodes(hasScrollToNodeAction())
        val scrollableCount = scrollables.fetchSemanticsNodes(atLeastOneRootRequired = false).size
        val reached =
            (0 until scrollableCount).any { index ->
                runCatching { scrollables[index].performScrollToNode(hasText(text)) }.isSuccess
            }
        check(reached) { "No scrollable container could reach '$text'" }
        composeRule.waitForIdle()
    }

    private fun openReminderSheetAndExpandSecondRepeat(repeatLabelSubstring: String) {
        scrollIntoViewAndClick(composeRule.onAllNodesWithText("Reminder").onFirst())
        waitForText("Set reminder")
        waitForText("• Monthly", substring = true)
        composeRule.onAllNodesWithText("• Monthly", substring = true).onFirst().performClick()
        waitForText("Reminder 2")
        waitForText(repeatLabelSubstring, substring = true)
        composeRule.onNodeWithText(repeatLabelSubstring, substring = true).performClick()
        waitForText("Every")
        waitForText("Ends")
    }

    private fun seedAndLaunch(
        themeConfig: DemoThemeConfig,
        includeTablet10Extras: Boolean = false,
        rheaStarred: Boolean = false,
        groceriesTag: String = "Shopping",
        groceriesStarred: Boolean = true,
        markdownNoteTitle: String = "Markdown Showcase",
        markdownIconKey: String? = "symbol:devices",
        useEndDateOnSecondReminder: Boolean = false,
        openNoteSelector: ((DemoNoteIds) -> Long)? = null,
        block: () -> Unit,
    ) {
        finishAllActivities()
        val noteIds =
            runBlocking {
                ScreenshotDemoData.resetAndConfigureEnvironment(targetContext, deps, themeConfig)
                ScreenshotDemoData.seedFullDemoNotes(
                    context = targetContext,
                    noteRepository = deps.noteRepository(),
                    includeTablet10Extras = includeTablet10Extras,
                    rheaStarred = rheaStarred,
                    groceriesTag = groceriesTag,
                    groceriesStarred = groceriesStarred,
                    markdownNoteTitle = markdownNoteTitle,
                    markdownIconKey = markdownIconKey,
                    useEndDateOnSecondReminder = useEndDateOnSecondReminder,
                )
            }
        val launchIntent =
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                openNoteSelector?.invoke(noteIds)?.takeIf { it > 0L }?.let { openNoteId ->
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, openNoteId)
                }
            }
        targetContext.startActivity(launchIntent)
        try {
            block()
        } finally {
            finishAllActivities()
        }
    }

    private fun finishAllActivities() {
        val stages =
            listOf(
                Stage.PRE_ON_CREATE,
                Stage.CREATED,
                Stage.STARTED,
                Stage.RESUMED,
                Stage.PAUSED,
                Stage.STOPPED,
            )
        val activitiesToFinish = mutableListOf<Activity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages.forEach { stage ->
                activitiesToFinish.addAll(monitor.getActivitiesInStage(stage))
            }
            activitiesToFinish.distinct().forEach { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
        }
        SystemClock.sleep(250L)
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 15_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForContentDescription(
        description: String,
        timeoutMillis: Long = 15_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun hideSoftKeyboardIfVisible(clearFocus: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumedActivities =
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
            val inputMethodManager =
                targetContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            resumedActivities.forEach { activity ->
                // Only clear focus when the keyboard is actually up: clearing it otherwise makes
                // Compose re-focus its first focusable and scroll it into view, which drags the
                // tablet two-pane list away from the top.
                val imeVisible =
                    ViewCompat
                        .getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (clearFocus && imeVisible) {
                    activity.currentFocus?.clearFocus()
                }
            }
            // Bottom sheets and dialogs are separate windows, so hide the IME from every
            // window, not just the activity's (same as FilePipe's harness).
            val windowRootViews =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WindowInspector.getGlobalWindowViews()
                } else {
                    resumedActivities.map { activity -> activity.window.decorView }
                }
            windowRootViews.forEach { rootView ->
                inputMethodManager?.hideSoftInputFromWindow(rootView.windowToken, 0)
            }
        }
    }

    private fun captureDeviceScreenshot(
        fileName: String,
        clearFocusBeforeCapture: Boolean = true,
    ) {
        composeRule.waitForIdle()
        hideSoftKeyboardIfVisible(clearFocus = clearFocusBeforeCapture)
        SystemClock.sleep(SETTLE_DELAY_MILLIS)
        composeRule.waitForIdle()

        val outputDirectory =
            targetContext.getExternalFilesDir(SCREENSHOT_SUBDIR)
                ?: File(targetContext.filesDir, SCREENSHOT_SUBDIR)
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val outputFile = File(outputDirectory, fileName)
        val bitmap: Bitmap =
            checkNotNull(
                InstrumentationRegistry
                    .getInstrumentation()
                    .uiAutomation
                    .takeScreenshot(),
            ) {
                "UiAutomation.takeScreenshot() returned null for $fileName"
            }
        try {
            FileOutputStream(outputFile).use { outputStream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    "Failed to encode JPEG screenshot to ${outputFile.absolutePath}"
                }
                outputStream.flush()
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val ARG_CAPTURE_SCREENSHOTS = "captureScreenshots"
        private const val SCREENSHOT_SUBDIR = "screenshots"
        private const val JPEG_QUALITY = 94
        private const val SETTLE_DELAY_MILLIS = 750L
    }
}
