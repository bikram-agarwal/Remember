# Remember Help Guide

## Bring your existing data

### Importing from Google Tasks

Remember can import your tasks from Google Tasks in two ways - by signing in directly or by loading a Google Takeout file. Both methods are strictly read-only, so Remember never edits, completes, or deletes anything in Google Tasks.

**Option 1: Connect Google (requires internet)**
1. On the Notes tab, tap `+` -> **Import**.
2. On the **Google Tasks** tab, tap **Choose Google account** (or **Continue as ...** if you have signed in before) and grant read-only access.
3. Once your tasks load, you can search, filter by task list, and check or uncheck individual tasks.
4. Choose how your tasks should be created in Remember:
   - **One note per task**: Creates a separate note for each task with its title, notes/details, and due date/reminder.
   - **One note per list**: Combines all selected tasks from each list into a single note.
   - **List as checklist**: Turns each task list into an interactive checklist in Remember.
5. Tap **Import** to add the selected items.

**Option 2: Google Takeout (no sign-in required; works in the Offline flavor)**
1. On the **Google Tasks** tab, tap **Open Google Takeout**, tap **Next step**, and then **Create export**.
2. Check your email for Google's download link, download the archive, and extract it on your device.
3. Return to Remember's Import screen, tap **Choose JSON file**, and select the `Tasks.json` file from the extracted Takeout folder.
4. Review your tasks, pick an import mode (**One note per task**, **One note per list**, or **List as checklist**), and tap **Import**.

### Importing from Tasks.org

1. In Tasks.org, open **Settings** -> **Backup** and create a backup. This saves a `user….json` file to your device.
2. In Remember, tap `+` -> **Import** and switch to the **Tasks.org & more** tab.
3. Tap **Choose files** and select the saved `user….json` backup file.
4. Just like Google Tasks, you can search, filter by list, choose whether to **Skip imported** or **Overwrite imported**, pick an import mode (**One note per task**, **One note per list**, or **List as checklist**), and tap **Import**.

### Importing and exporting Markdown

Remember supports `.md`, `.markdown`, and `.txt` files (up to 512 KB per file) for both notes and checklists:

- **Bulk import from the Import screen**:
  1. Tap `+` -> **Import** and switch to the **Tasks.org & more** tab.
  2. Tap **Choose files** and select one or more `.md` or `.markdown` files, plain-text `.txt` files, `.zip` archives containing Markdown files.
  3. Each file's name (without the `.md`, `.markdown`, or `.txt` extension) becomes the title of the imported note or list.
  4. **Automatic checklist detection**: If an imported Markdown file contains only checklist items (`- [ ]` or `- [x]`, with optional one-level nesting and indented detail lines, and no headings, code blocks, quotes, tables, or dividers), Remember automatically creates it as an interactive **checklist** instead of a note.
- **Import into a new note or checklist**: On a new, empty note or checklist, tap the top-right `⋮` menu and choose **Import MD** to load a `.md` or `.markdown` file into it.
- **Export a single note or checklist**: Open any note or checklist with content and tap `⋮` -> **Export MD** to save it as a `.md` file to your device storage.
- **Bulk ZIP export**: Long-press any item on the Notes tab to enter multi-select mode, select the notes and checklists you want, and tap **Export** in the bottom selection bar to save a `.zip` archive containing a `.md` file for each selected item.

### Importing/Restoring Remember backup

You can create, import, and restore Remember backup `.zip` files from **Settings** -> **Backup**, or load a backup `.zip` directly via **Tasks.org & more -> Choose files** or **Drag and drop**.

#### What a backup includes

Backups include notes, lists, checklist items, tags, reminder details, visibility, importance, icons, actions, archive/trash/done state, attachment metadata, and app settings.

Enable **Include media in backup** to embed pictures and attachment files in the backup. This makes the file larger but means images and attachments will restore correctly on another device.

#### Import vs. restore

**Import backup** adds the backup's notes to your existing collection. If the same notes already exist, importing again creates duplicates. Use import when you want to merge data.

**Restore backup** replaces all current app data with the backup. It is destructive - current notes and lists are wiped before the backup loads. Use restore when you want to replace your data, not add to it.

**If media is missing after restore:** the backup may not have included media files. If media was stored only as file links, those links may not resolve on a different device. Create future backups with **Include media in backup** enabled.

### Drag and drop

On tablets, foldables, ChromeOS, or Android split-screen/multi-window, you can drag files directly from a file manager or another app and drop them onto the unlocked Remember window:

- **Single `.md`, `.markdown`, or `.txt` file**: Immediately creates a new note (or checklist if the file only contains `- [ ]` / `- [x]` checklist items) and opens it right away.
- **Single Remember backup `.zip` file**: Prompts you to either **Import** its notes into your existing collection or **Restore** (replace all current notes, lists, and settings).
- **Multiple files or `.zip` archives**: Imports all valid `.md`, `.markdown`, plain-text, zipped Markdown files, and Remember backup `.zip` files (merged additively) in bulk, then shows how many notes were imported.
- **Single `.json` file**: Opens the Import screen with your Google Tasks Takeout (`Tasks.json`) or Tasks.org (`user….json`) backup already loaded so you can review and choose what to import.

While a note or checklist is in edit mode, file import is turned off so that text you drag in from another app goes into the note instead. Switch to view mode, or go back to the notes list, to drop files.

### If an import fails

- **Connect Google**: Check your internet connection, or try **Switch account** if sign-in or consent fails.
- **JSON files**: Make sure you select the `Tasks.json` file from an extracted Google Takeout archive or a Tasks.org `user….json` backup - Remember does not accept generic JSON files.
- **Markdown, text, and ZIP files**: Each `.md`, `.markdown`, or `.txt` file must be non-empty and under 512 KB. If a `.zip` archive fails, verify that it contains valid `.md` / `.markdown` files or a Remember backup.

---

## Getting started

### Notes vs. lists

- Use a **note** when you want free-form text — details, instructions, links, or longer context.
- Use a **list** when the work is made of steps you can check off.

### Creating notes and lists

- Tap the create button on the Notes tab and choose **Note**, **Checklist**, or **Import**.
- Use launcher shortcuts for quick note or list creation.
- Add a home-screen widget for one-tap creation from the launcher.
- Turn on **Quick capture notification** in Settings for a persistent shortcut that is always one swipe away.

### Marking something done

Marking a note or list done moves it out of your active work into the done area.
- For a one-time reminder, marking done clears the reminder notification.
- For a recurring reminder, marking done advances the item to the next scheduled occurrence. If no future occurrence exists, the item moves to done like a one-time reminder.

---

## Notes and lists

### What you can set on a note or list

Each note and list supports: an icon, tags, reminder, notification action, importance level, visibility setting, picture, and attachments. Pictures appear as cover images on cards and in reminder notifications. Attachments stay connected to the note they belong to.

### Note formatting

Notes support Markdown-style writing: headings, bold, italic, underline, strikethrough, lists, quotes, code blocks, links, and dividers. Reminder notifications use a plain-text version of the note so they stay readable.

Text you copy from a web page, email, or document keeps its formatting when you paste it into a note: headings, lists, checkboxes, quotes, code, links, and bold or italic text become the matching Markdown. Tables keep their text, one row per line. Text dragged in from another app, or pasted from your keyboard's clipboard suggestions, arrives as plain text.

### Checklists

Lists contain checkable items you can reorder, and supports one level of nesting. Indent an item under a parent to group related subtasks. Checking a parent item checks its children. If every child under a parent becomes checked, the parent checks itself too. Completed items can be separated from active ones.

### Converting between notes and checklists

You can convert an existing note into a checklist or a checklist into a note from the top-right `⋮` menu:

- **Convert to checklist**: Available on notes whose body contains only list items — either Markdown checkboxes (`- [ ]` / `- [x]`) or plain bulleted/numbered items (`-`, `*`, `+`, or `1.`), with up to one level of nesting and optional indented detail lines. If the note contains headings, code blocks, quotes, tables, dividers, or regular paragraphs, **Convert to checklist** is hidden so formatting isn't lost.
- **Convert to note**: Available on any checklist. Converts all checklist items (preserving checked state, one-level nesting, and item details) into `- [ ]` / `- [x]` Markdown checklist syntax inside a standard note.

Your title, star state, reminder, tags, importance, visibility, icon, picture, attachments, and notification action are all preserved when converting.

---

## Organization

### Tags, stars, pins, archive, and trash

- **Tags** group related notes and lists for quick filtering.
- **Star** marks important items so they surface easily.
- **Pin** keeps an item at the very top of the Notes tab.
- **Archive** removes items from the active list without deleting them.
- **Trash** is for items you intend to remove.

### Pinning

Pinned notes and lists collect in a **Pinned** section at the very top of the Notes tab. The section stays there no matter how you sort or group the list — pinning is about placement, and starring is about marking a favorite, so the two are separate and can be used together.
- Pin or unpin from the note's bottom bar, by swiping a card right (Pin is the first reveal action by default), or by selecting several notes and using **Pin** in the selection bar.
- Searching or filtering can hide a pinned item, because those narrow down what the list contains. Changing the sort or grouping never will. 
- Marking a pinned item done moves it to the **Done** section at the bottom, like anything else. It stays pinned, so it returns to the top if you mark it not done again.
- Duplicating a pinned item does not pin the copy.

### Sorting and grouping

Use the **Sort** and **Group** chips on the Notes tab to organize your notes and checklists. You can sort or group by dates, title, reminders, and importance, and toggle the sort direction (Ascending / Descending) from the Sort menu.

### Search

Search checks titles, note bodies, tags, and checklist items. It also finds matches in archived and trashed notes.

### Finding archived or trashed notes

Archived and trashed notes are hidden from the main Notes tab intentionally. Open the **History tab** to review them. Search is also useful when looking for something specific you archived or trashed.

---

## Reminders

### How reminders work

Add a reminder date and time to any note or list. When the time arrives, Remember posts a notification. Tapping it opens the note directly.

The reminder sheet quick chips follow **Preset type** in Settings -> Notifications.
- **Timing** offers named times: Soon, Later today, This evening etc.
- **Duration** offers lengths of time: 30 mins, 6 hours, 12 hours, 7 days etc.

Reminder notifications can include the title, note content or list items, a picture, snooze, mark done, and one custom action button.

### Importance: Low, Normal, High, and Critical

Importance controls how loud a reminder is when it fires - not when it fires.
- **Low** is silent, with no status bar icon or vibration.
- **Normal** uses the standard sound and vibration.
- **High** pops up as a heads-up alert and is treated as an alarm. Use it when a reminder needs to be immediately noticeable.
- **Critical** re-alerts every 15 minutes until snoozed, marked done, or lowered in importance. Overdue items ring together. Swiping the notification away will not stop repeats. Opening the notification shade quiets an active alert early.
  - Alarm Mode (30 sec, 1 min, etc.): Acts as a full system alarm using your alarm volume and tone. It overrides silent/vibrate modes and passes through Do Not Disturb (if DND allows alarms).
  - Chime Mode (Single chime): Plays your High-importance notification sound once per 15-minute alert cycle. It respects silent mode and DND limits.
  - Configuration: Switch modes or change alarm duration in Settings → Notifications → Critical reminder type. To customize the alarm sound itself, edit the Reminders (Critical) channel in Android System Settings.

### Recurring reminders

Recurring reminders can repeat daily, weekly, monthly, or yearly. You can end the recurrence on a date or after a set number of occurrences.

When you mark a recurring reminder done, Remember advances it to the next scheduled occurrence. If there is no future occurrence, the item moves to done.


### Restore notifications

**Restore notifications** is a Settings option for persistent reminders, saving them from being accidentally swiped away. When enabled, a dismissed reminder notification will come back immediately if the note or list is still not marked done.

### Pinning notifications

You can also manually pin a note or list's notification from its bottom bar. A manually pinned notification stays active, is automatically restored if dismissed, and comes back after your device restarts, until you unpin it or mark the item done.

### Reminder summary

The **reminder summary notification** is a quiet, persistent overview of overdue and upcoming reminders. It makes no sound and works as a status panel you can glance at without opening Remember.

### Notification permission and reliability

Android requires notification permission before Remember can post reminder alerts, snooze actions, and quick capture. The Notifications section in Settings shows the permissions available on your device. Some devices expose a separate background or battery reliability option — granting it makes reminders less likely to be delayed by battery optimization. The exact options vary by Android version and manufacturer.

### Snooze

Snooze moves a reminder to a later time without opening the app. The snooze sheet follows **Preset type** in Settings -> Notifications.
- **Timing** offers named times: soon, later today, this evening, tomorrow, next week.
- **Duration** replaces those with a duration: 1-60 minutes, 1-24 hours, or 1-30 days. Choose the number and unit, then tap **Snooze**.
**Pick a specific time** stays available in both modes, with a date and time picker.

### Troubleshooting reminders

**Not getting reminders:**
- Check that Android notifications are allowed for Remember.
- Confirm the note or list still has a reminder set.
- Check whether the item was marked done, archived, or moved to trash.

**Reminder appeared late:**
- Android can delay apps due to battery saver or sleep restrictions.
- Go to Settings -> Notifications and enable the reliability options shown on your device.
- If your device shows a background or battery exception for Remember, allow it.

---

## Notification actions

### How they work

Notification actions are buttons you can attach to a reminder notification. They let you act on the reminder directly — a reminder to call someone can show a call button right in the notification, so you can place the call from right there.

You can add one custom action per note. Reminder notifications always include **Snooze** and **Mark as done** regardless.

Available actions: call, message, email, directions, open a link, open an app, open a shortcut, copy to clipboard, share content.

Some actions hand off to another app — calls may open the phone app, directions may open a maps app, links may open a browser. That is expected behavior. If the target app is missing or the action data is invalid, the action will not run.

### Troubleshooting notification actions

- Confirm the action data is valid: phone number, email address, URL, address, app, shortcut, or text.
- Check that the required app is installed for call, directions, email, and link actions.
- Secret visibility can prevent note content from being used in action data — check the note's visibility setting.

---

## Widgets and quick capture

### Available widgets

- **Agenda widget** — shows overdue and upcoming reminders.
- **Selected notes widget** - showing notes and lists you choose: all, starred, pinned, or selected tag. 
- **Quick capture widget** — buttons to create a new note or list directly from the home screen.

### Quick capture notification

The **quick capture notification** is a persistent shortcut for creating a new note without opening the app. Turn it on in Settings. It is useful when you often need to capture something while doing something else.

### Troubleshooting widgets

- Secret notes do not show up in widgets.
- Private notes' content is always hidden in the widget.
- Widgets show active items only — archived and trashed items do not appear.
- The agenda widget shows reminder items due now or within next 7 days, not every note.

---

## Privacy and security

### Where notes are stored

Remember stores notes on your device in a local database. The database is not encrypted — data is stored in plaintext. Android's app sandboxing prevents other apps from accessing it, but the data itself is not encrypted at rest.

Remember supports Android Auto Backup. Your notes, lists, tags, and settings are automatically backed up to your Google account and restored when you reinstall the app on a new device. Attachments and pictures are not included in Auto Backup — use the in-app backup with **Include media in backup** enabled if you need those to transfer as well.

### Visibility: Normal, Private, and Secret

Visibility controls what appears in notifications and widgets — it is not a sharing or encryption setting.
- **Normal** allows the title and note content to appear in notifications and widgets.
- **Private** hides notification body content and widget preview text.
- **Secret** hides both the note title and body from notification and keeps the item out of widgets entirely.

### App lock

App lock requires your device lock — or biometrics, if your device supports it — to open Remember. It prevents casual access to the app but does not encrypt the underlying data.

---

## Customization

### Appearance

**Theme mode**
System, Light, Dark, or Black. Black uses a true black background rather than dark grey — sharper on OLED screens. It also disables gradient and enhanced shading, since those effects are designed for non-black backgrounds.

**Color source**
- **Material You** — pulls colors from your wallpaper on Android 12 and above, and updates automatically when the wallpaper changes.
- **Presets** — eight hand-tuned schemes: Forest, Ember, Grove, Honey, Ocean, Iris, Dusk, and Berry.
- **Custom color** — enter any hex value. You can save multiple custom colors and switch between them.

**Palette style**
Controls how Material 3 expands your seed color into a full palette. The same seed can feel very different across styles. Options: Tonal Spot (default), Vibrant, Expressive, Rainbow, Fruit Salad, Neutral, Monochrome, Fidelity, Content. Palette style applies to presets and custom colors; Material You manages its own palette.

**Visual effects**
- **Gradient background** — blends your primary color into the background for tinted depth rather than a flat surface. Disabled in Black mode.
- **UI scale** - makes the whole interface smaller or larger (75% to 125%). Text, icons, and spacing change together.
- **Enhanced shading** — makes cards darker in dark mode, lighter in light mode. Disabled in Black mode.
- **Blur bars** — frosted glass effect on the top and bottom bars, so content scrolls behind them.

### Notes UI

- **Adaptive note themes** — tints the note page with color taken from its cover image.
- **Cover image on cards** — shows a note's picture as a soft background on the note card, not just inside the note.
- **Note content on cards** — shows the note body or list items on the note card.

### Defaults

**Defaults** in Settings sets how new notes and lists start, so you don't have to change the same options every time:
- **Default visibility** and **Default importance** apply to every new note and list.
- **Default reminder time** is the time of day used for new reminders.
- **Default recurrence** is the repeat rule preselected when you turn on repeat for a reminder.

Changing a default does not change notes and lists you already have.

### Swipe actions

**Reveal mode** (default) — swiping a note reveals a row of action buttons rather than immediately doing something. Each direction (right swipe and left swipe) has three slots, giving you six configurable actions in total. You can long-press and drag actions to swap their positions between slots. Default layout: right swipe shows Pin, Star, Duplicate; left swipe shows Mark done, Archive, Trash.

Reveal mode holds six of the seven available actions, and its editor rearranges the six that are there rather than swapping in a different one. Edit is the action left out of the default layout — it is one tap away inside the note anyway. To use Edit on swipe, choose **Direct mode**, where every action is selectable.

**Direct mode** — swiping immediately executes a single action with no intermediate step. One action per direction. Useful if you have a single operation you want to do as fast as possible. Defaults: swipe right pins, swipe left moves to trash — the same as each direction's first reveal action.

Available actions for either mode: Pin, Star, Duplicate, Mark done, Archive, Trash, Edit.

Haptic feedback at swipe thresholds can be turned on or off independently of the mode.

## Offline flavor

### What's different in the Offline flavor

The Offline flavor is a separate build of Remember with no internet permission at all. It's available from GitHub Releases as `remember-v<version>-offline.apk`. Compared with the other versions:
- **No internet permission.** Remember can't connect to internet.
- **No update checks.** Remember can't look for new versions itself. Use **Track updates via ObtainX** on the Settings page (under Updates on tablets), or install a newer offline APK from GitHub Releases by hand.
- **No changelog after an update.** The other versions show what's new the first time you open them after updating.
- **No Connect Google.** Import tasks from a Google Takeout or Tasks.org file instead.
- **Installs alongside the other versions** as a separate app, with its own notes and settings. To move your notes over, make a backup in one and restore it in the other, then grant permissions such as notifications again.
