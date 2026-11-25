package dev.bikram.remember.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.core.content.FileProvider
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.IconPickerPrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.MonthlyMode
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteLayoutMode
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.PersistableChecklistItem
import dev.bikram.remember.data.RecurrenceEndKind
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.di.DevOptionsDependenciesEntryPoint
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar

internal data class DemoNoteIds(
    val airFilterId: Long,
    val rheaBirthdayId: Long,
    val groceriesId: Long,
    val humbleBundleId: Long,
    val useOtherCardsId: Long,
    val markdownShowcaseId: Long,
)

internal data class DemoThemeConfig(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val useBlackTheme: Boolean = false,
    val colorSource: ColorSource = ColorSource.DEFAULT,
    val paletteStyle: PaletteStyleOpt = PaletteStyleOpt.TONAL_SPOT,
    val customSeeds: List<String> = emptyList(),
    val activeCustomSeed: String = "",
    val noteLayoutMode: NoteLayoutMode = NoteLayoutMode.LIST,
    /** Tint an open note's page from its cover image (off = same colours as the list pane). */
    val adaptiveNoteThemes: Boolean = true,
)

internal object ScreenshotDemoData {
    private const val DEMO_MARKDOWN_HERO_ASSET = "demo_markdown_hero.webp"
    private const val DEMO_TASKS_ATTACHMENT_ASSET = "demo_attachment.txt"
    private const val DEMO_MARKDOWN_HERO_FRAMING = """{"v":1,"fx":0.5,"fy":0.4262000024318695,"z":1}"""

    private const val COLOR_INDEX_NONE = 0
    private const val COLOR_INDEX_ROSE = 1
    private const val COLOR_INDEX_SUN = 3
    private const val COLOR_INDEX_SKY = 6
    private const val COLOR_INDEX_ORCHID = 8

    val markdownShowcaseBody: String =
        """
        # Heading 1
        ## Heading 2
        ### Heading 3
        **Bold**, *italic*, <u>underline</u>, ~~strikethrough~~

        > A blockquote line

        - Bullet item 1
          - Child item 1

        1. Ordered item 1
          1. Child item 1

        - [ ] Checklist in note
          - [ ] Child item 1

        `inline code` and [a link](https://example.com)

        ```
        # Compose FTW
        echo "Multiline code"
        ```
        """.trimIndent()

    suspend fun resetAndConfigureEnvironment(
        context: Context,
        deps: DevOptionsDependenciesEntryPoint,
        themeConfig: DemoThemeConfig,
    ) {
        deps.noteRepository().deleteAllNotes()
        // Starred icons/emojis would add a "Favorites" row to the icon picker shots.
        val iconPickerPrefs = IconPickerPrefs(context.applicationContext)
        iconPickerPrefs.setStarredIconKeys(emptyList())
        iconPickerPrefs.setStarredEmojis(emptyList())
        deps.lockPrefs().reset()
        deps.notesUiPrefs().reset()
        deps.interactionPrefs().reset()
        deps.reminderPrefs().reset()
        deps.defaultNotePrefs().reset()
        deps.quickCapturePrefs().reset()
        deps.backupPrefs().reset()
        deps.devModePrefs().setEnabled(false)

        deps.onboardingPrefs().markIntroSeen()
        deps.updatePrefs().reset()
        deps.updatePrefs().setUpdateCheckSchedule(UpdateCheckSchedule.NEVER)
        deps.updatePrefs().setLastSeenAppVersion(BuildConfig.VERSION_NAME)

        val themePrefs = deps.themePrefs()
        themePrefs.reset()
        themePrefs.setThemeMode(themeConfig.themeMode)
        themePrefs.setUseBlackTheme(themeConfig.useBlackTheme)
        themePrefs.setPaletteStyle(themeConfig.paletteStyle)
        themeConfig.customSeeds.forEach { seedHex ->
            themePrefs.addCustomSeed(seedHex)
        }
        if (themeConfig.colorSource == ColorSource.CUSTOM && themeConfig.activeCustomSeed.isNotBlank()) {
            themePrefs.setActiveCustomSeed(themeConfig.activeCustomSeed)
        } else {
            themePrefs.setColorSource(themeConfig.colorSource)
        }

        deps.notesUiPrefs().setAdaptiveNoteThemes(themeConfig.adaptiveNoteThemes)
        deps.notesUiPrefs().setHeroOnCards(true)
        deps.notesUiPrefs().setShowNoteContentOnCards(true)

        deps.viewOptionsPrefs().setViewOptions(
            ViewOptions(
                sortKey = SortKey.REMINDER,
                sortDir = SortDir.ASC,
                groupBy = GroupBy.DATE,
                noteLayoutMode = themeConfig.noteLayoutMode,
                settingsCollapsedSectionKeys =
                    listOf(
                        "notes_ui",
                        "defaults",
                        "haptics",
                        "swipe",
                        "security",
                        "backup",
                        "updates",
                    ),
            ),
        )
    }

    suspend fun seedFullDemoNotes(
        context: Context,
        noteRepository: NoteRepository,
        includeTablet10Extras: Boolean = false,
        rheaStarred: Boolean = false,
        groceriesTag: String = "Shopping",
        groceriesStarred: Boolean = true,
        markdownNoteTitle: String = "Markdown Showcase",
        markdownIconKey: String? = "symbol:devices",
        useEndDateOnSecondReminder: Boolean = false,
    ): DemoNoteIds {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val airFilterMillis = epochMillis(today.minusDays(29), LocalTime.of(9, 0), zone)
        val epicGamesMillis = epochMillis(today.minusDays(2), LocalTime.of(10, 0), zone)
        val rheaBirthdayMillis = epochMillis(today, LocalTime.of(23, 58), zone)
        val groceriesMillis = epochMillis(today.plusDays(2), LocalTime.of(18, 0), zone)
        // Reminder dates must agree with their weekday rules (the sheet shows date and rule
        // together): reminder 1 on a Tuesday, reminder 2 on the occurrence its monthly rule picks.
        val humblePrimaryDate = today.plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
        val humbleSecondaryDate =
            if (useEndDateOnSecondReminder) {
                today.plusMonths(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.TUESDAY))
            } else {
                today.plusMonths(1).with(TemporalAdjusters.lastInMonth(DayOfWeek.TUESDAY))
            }
        val humblePrimaryMillis = epochMillis(humblePrimaryDate, LocalTime.of(10, 0), zone)
        val humbleSecondaryMillis = epochMillis(humbleSecondaryDate, LocalTime.of(9, 0), zone)
        val humbleEndDateMillis = epochMillis(today.plusMonths(7).withDayOfMonth(28), LocalTime.of(23, 59), zone)
        val cardsMillis = epochMillis(today.plusDays(20), LocalTime.of(9, 0), zone)

        val airFilterId =
            noteRepository.createNote(
                title = "Air Filter Replacement",
                body = "Last replaced on [11/15/2025](date:2025-11-15)",
                colorIndex = COLOR_INDEX_SKY,
                options =
                    NoteOptions(
                        reminderAt = airFilterMillis,
                        iconKey = "symbol:air_purifier",
                        tags = listOf("Errands"),
                    ),
            )

        if (includeTablet10Extras) {
            val epicReminders =
                listOf(
                    NoteReminder(
                        reminderAt = epicGamesMillis,
                        recurrence = RecurrenceRule(unit = RecurrenceUnit.WEEK, interval = 1),
                    ),
                )
            noteRepository.createNote(
                title = "Epic Games",
                body = "",
                colorIndex = COLOR_INDEX_ROSE,
                options =
                    NoteOptions(
                        reminderAt = epicGamesMillis,
                        recurrence = epicReminders.first().recurrence,
                        reminders = epicReminders,
                        iconKey = "drawable:ic_brand_epicgames",
                        tags = listOf("Entertainment"),
                    ),
            )
        }

        val heroUri = copyDemoMarkdownHeroUri(context)
        val rheaReminders =
            listOf(
                NoteReminder(
                    reminderAt = rheaBirthdayMillis,
                    recurrence = RecurrenceRule(unit = RecurrenceUnit.YEAR, interval = 1),
                ),
            )
        val rheaBirthdayId =
            noteRepository.createNote(
                title = "Rhea Birthday",
                body =
                    """
                    **30 May IST**
                    - [ ] Call in the morning
                    - [ ] Buy gift 🎁
                      - [ ] Something thoughtful
                    - [ ] Meet for dinner
                    """.trimIndent(),
                colorIndex = COLOR_INDEX_NONE,
                options =
                    NoteOptions(
                        reminderAt = rheaBirthdayMillis,
                        recurrence = rheaReminders.first().recurrence,
                        reminders = rheaReminders,
                        importance = Importance.HIGH,
                        pictureUri = heroUri,
                        pictureHeroFraming = heroUri?.let { DEMO_MARKDOWN_HERO_FRAMING },
                        iconKey = "symbol:cake",
                        actions =
                            listOf(
                                NoteAction(
                                    type = ActionType.CALL_NUMBER,
                                    title = "Call Rhea",
                                    details = "+11234567890",
                                    // What picking a contact stores: the contact's photo, as PNG data.
                                    iconData = demoContactAvatarData("💕"),
                                ),
                            ),
                        tags = listOf("Birthday 🎂"),
                    ),
            )
        if (rheaStarred) {
            noteRepository.setStarred(rheaBirthdayId, true)
        }

        val groceriesReminders =
            listOf(
                NoteReminder(
                    reminderAt = groceriesMillis,
                    recurrence = RecurrenceRule(unit = RecurrenceUnit.WEEK, interval = 2),
                ),
            )
        val groceriesOptions =
            NoteOptions(
                reminderAt = groceriesMillis,
                recurrence = groceriesReminders.first().recurrence,
                reminders = groceriesReminders,
                iconKey = "symbol:grocery",
                actions =
                    listOf(
                        NoteAction(
                            type = ActionType.GET_DIRECTIONS,
                            title = "QFC Direction",
                            details = "1234",
                        ),
                    ),
                tags = listOf(groceriesTag),
            )
        val groceriesId =
            noteRepository.createListWithItems(
                title = "Groceries",
                colorIndex = COLOR_INDEX_SUN,
                items = groceriesChecklistItems(),
                options = groceriesOptions,
            )
        copyDemoTasksAttachmentUri(context, groceriesId)?.let { attachmentUri ->
            noteRepository.addAttachment(
                noteId = groceriesId,
                uri = attachmentUri,
                displayName = "Tasks.txt",
                mimeType = "text/plain",
            )
        }
        if (groceriesStarred) {
            noteRepository.setStarred(groceriesId, true)
        }

        val secondMonthlyRule =
            if (useEndDateOnSecondReminder) {
                RecurrenceRule(
                    unit = RecurrenceUnit.MONTH,
                    interval = 1,
                    monthlyMode = MonthlyMode.ByNthWeekday(ordinal = 1, weekday = Calendar.TUESDAY),
                    endKind = RecurrenceEndKind.ON_DATE,
                    endDate = humbleEndDateMillis,
                )
            } else {
                RecurrenceRule(
                    unit = RecurrenceUnit.MONTH,
                    interval = 1,
                    monthlyMode = MonthlyMode.ByNthWeekday(ordinal = 5, weekday = Calendar.TUESDAY),
                    endKind = RecurrenceEndKind.NEVER,
                )
            }
        val humbleReminders =
            listOf(
                NoteReminder(
                    reminderAt = humblePrimaryMillis,
                    recurrence = RecurrenceRule(unit = RecurrenceUnit.MONTH, interval = 1),
                ),
                NoteReminder(
                    reminderAt = humbleSecondaryMillis,
                    recurrence = secondMonthlyRule,
                ),
            )
        val humbleBundleId =
            noteRepository.createNote(
                title = "HumbleBundle Choice",
                body =
                    """
                    **June 2026** games were:
                    - [x] Octopath Traveler II
                    - [ ] The Riftbreaker
                    - [x] Life is Strange: Double Exposure
                    - [x] Indika
                    - [x] Citizen Sleeper 2
                    - [ ] Content Warning
                    - [ ] Hell Let Loose
                    - [ ] Overcooked! 2
                    - [ ] Flower
                    """.trimIndent(),
                colorIndex = COLOR_INDEX_ROSE,
                options =
                    NoteOptions(
                        reminderAt = humblePrimaryMillis,
                        recurrence = humbleReminders.first().recurrence,
                        reminders = humbleReminders,
                        iconKey = "drawable:ic_brand_humblebundle",
                        tags = listOf("Entertainment"),
                    ),
            )

        val useOtherCardsId =
            noteRepository.createNote(
                title = "Use Other Cards",
                body =
                    """
                    - BofA last used on [12/03/2025](date:2025-12-03).
                    - Amex last used on [01/26/2026](date:2026-01-26).
                    - CapOne last used on [04/08/2026](date:2026-04-08).
                    """.trimIndent(),
                colorIndex = COLOR_INDEX_ORCHID,
                options =
                    NoteOptions(
                        reminderAt = cardsMillis,
                        iconKey = "symbol:credit_card",
                        tags = listOf("Finance 💵"),
                    ),
            )

        val markdownShowcaseId =
            if (includeTablet10Extras || markdownNoteTitle.isNotEmpty()) {
                noteRepository.createNote(
                    title = if (includeTablet10Extras) "Demo: Markdown" else markdownNoteTitle,
                    body = markdownShowcaseBody,
                    colorIndex = COLOR_INDEX_SKY,
                    options =
                        NoteOptions(
                            iconKey = markdownIconKey,
                            tags = listOf(RememberReservedTags.MOCK),
                        ),
                )
            } else {
                -1L
            }

        val tagRepo = noteRepository.tagRepository
        tagRepo?.setTagColor("Errands", if (groceriesTag == "Errands") "#FF7043" else "#26C6DA")
        tagRepo?.setTagColor("Birthday 🎂", "#FDD835")
        tagRepo?.setTagColor("Shopping", "#FFA726")
        tagRepo?.setTagColor("Entertainment", "#EF5350")
        tagRepo?.setTagColor("Finance 💵", "#AB47BC")

        return DemoNoteIds(
            airFilterId = airFilterId,
            rheaBirthdayId = rheaBirthdayId,
            groceriesId = groceriesId,
            humbleBundleId = humbleBundleId,
            useOtherCardsId = useOtherCardsId,
            markdownShowcaseId = markdownShowcaseId,
        )
    }

    /**
     * Stand-in for a contact's photo, drawn like a Google Contacts monogram (the glyph on a soft
     * round tile; here an emoji), encoded like [toNoteActionIconData] does.
     */
    private fun demoContactAvatarData(initial: String): String? {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8BBD0.toInt() }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, fill)
            val text =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = size * 0.5f
                    textAlign = Paint.Align.CENTER
                }
            canvas.drawText(initial, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun groceriesChecklistItems(): List<PersistableChecklistItem> =
        listOf(
            PersistableChecklistItem(-1, "Drinkables", false, 1.0),
            PersistableChecklistItem(-2, "Coffee ☕", false, 2.0, -1, 1),
            PersistableChecklistItem(-3, "Milk 🥛", false, 3.0, -1, 1),
            PersistableChecklistItem(-4, "Cola 🥤", true, 4.0, -1, 1),
            PersistableChecklistItem(-5, "Snacks", false, 5.0),
            PersistableChecklistItem(-6, "Cookies 🍪", false, 6.0, -5, 1),
            PersistableChecklistItem(-7, "Banana 🍌", false, 7.0, -5, 1),
            PersistableChecklistItem(-8, "Grooming", false, 8.0),
            PersistableChecklistItem(-9, "Toothpaste / Brush 🪥", false, 9.0, -8, 1),
            PersistableChecklistItem(-10, "Shaving kit 🪒", false, 10.0, -8, 1),
        )

    private fun epochMillis(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): Long =
        date
            .atTime(time)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun copyDemoMarkdownHeroUri(context: Context): String? =
        copyAssetToFileProviderUri(
            context = context,
            assetName = DEMO_MARKDOWN_HERO_ASSET,
            destination = File(context.filesDir, "note_heroes/demo_markdown_hero.webp"),
        )

    private fun copyDemoTasksAttachmentUri(
        context: Context,
        noteId: Long,
    ): String? =
        copyAssetToFileProviderUri(
            context = context,
            assetName = DEMO_TASKS_ATTACHMENT_ASSET,
            destination = File(context.filesDir, "note_attachments/$noteId/demo_attachment.txt"),
        )

    private fun copyAssetToFileProviderUri(
        context: Context,
        assetName: String,
        destination: File,
    ): String? =
        runCatching {
            destination.parentFile?.mkdirs()
            context.assets.open(assetName).use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination).toString()
        }.getOrNull()
}
