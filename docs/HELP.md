# Remember Help Guide

## Getting started

### Notes vs. lists
- Use a **note** for free-form content such as details, instructions, links, or longer context.
- Use a **list** when you have items or steps to check off, such as a grocery list or a set of chores.

### Creating notes and lists
You can create a note or checklist from inside Remember or while using another app:

- On the Notes tab, tap the `+` create button and choose **Note** or **Checklist**.
- Use a launcher shortcut, home-screen widget, or the **Quick capture notification** to start one without opening Remember. Enable the notification in Settings.
- Share text, a link, an image, or a file from another app to Remember. You can also open supported files with Remember. See **Add content from other apps and files**.
- To move data from another service or restore a Remember backup, see **Bring your existing data**.

### What you can set on a note or list
Each note and list can have an icon, tags, a reminder, a notification action, an importance level, a visibility setting, a picture, and attachments.

Pictures appear as cover images on cards and in reminder notifications. Attachments stay connected to the note they belong to.

### Note formatting
Notes support Markdown-style formatting, including:
- Headings, **bold**, *italic*, <u>underline</u>, and ~~strikethrough~~
- Bulleted, numbered, and checklist lists
- Quotes, `inline-code`, code blocks, links, and dividers

Reminder notifications use a plain-text version of the note to keep the content readable.

Text copied from a web page, email, or document keeps its formatting when pasted into a note, except for tables. Text dragged in from another app, or pasted from your keyboard's clipboard suggestions, arrives as plain text.

**Date chips**
Date chips are tappable dates, such as `08/15/2027`, that you can add anywhere in a note. They help you keep track of dates without setting a full reminder.

- **Insert:** Tap the calendar icon in the formatting toolbar and choose a date, or select an existing date in your text to turn it into a chip.
- **Update:** Tap a chip to change its date in the picker. You can also edit its text directly; it turns back into a chip after you pause typing.
- **More actions:** Long-press a chip to **Change date**, **Open in Calendar**, or **Copy date**.

Date chips are stored as ordinary Markdown links, so they survive export and import.

### Checklists
Checklists contain checkable items that you can reorder. They support one level of nesting, so you can indent items under a parent to group related subtasks.

Checking a parent item checks its children. If every child under a parent is checked, the parent checks itself too.

You can separate completed items from active ones and add brief context text to each item.

### Converting between notes and checklists

Use the top-right `⋮` menu to convert an existing note into a checklist or a checklist into a note.

**Convert to checklist** appears on notes whose body contains only list items: Markdown checkboxes (`- [ ]` or `- [x]`) or plain bulleted or numbered items (`-`, `*`, `+`, or `1.`). Up to one level of nesting and optional indented detail lines are supported. If the note contains headings, code blocks, quotes, tables, dividers, or regular paragraphs, this option is hidden so formatting isn't lost.

**Convert to note** is available on any checklist. It converts the items into Markdown checkbox syntax (`- [ ]` or `- [x]`) inside a standard note, preserving checked state, one-level nesting, and item details.

Conversion preserves the title, star state, reminder, tags, importance, visibility, icon, picture, attachments, and notification action.

## Add content from other apps and files

Save text, links, images, and files to Remember directly from other apps, without interrupting what you're doing.

### Share or open a file with Remember

**Share** is the most flexible option. In another app, tap **Share** and choose **Remember**. You can share selected text, links, web content, one or more images, or any type of file.

**Open with** is available only when opening a single file with one of these extensions: `.md`, `.markdown`, `.txt`, `.zip`, or `.json`. You'll find it in file managers and other apps that offer an *Open with* action.

When in doubt, use **Share**. It accepts more content types and lets you review and edit content before saving.

What happens next depends on the content type. See **How incoming files are handled** below.

### How incoming files are handled

Remember recognizes certain file types and handles them automatically:

- **One Markdown or text file** (`.md`, `.markdown`, or `.txt`): Creates a note, or a checklist if the file contains only checklist items, and opens it in the editor.
- **Multiple Markdown or text files** (`.md` or `.txt`), or a Markdown `.zip`: Imports the files together and opens the Notes tab with the new notes selected and scrolled into view, ready for a bulk action.
- **Remember backup `.zip`:** Asks whether to **Import** (merge) or **Restore** (replace everything), then opens the Notes tab. See **Importing or restoring a Remember backup**.
- **Google Takeout or Tasks.org `.json`:** Opens the Import screen with the file loaded. See **Importing from Google Tasks** or **Importing from Tasks.org**.
- **Other files** (PDFs, documents, audio, video, and so on): Opens the **Save to Remember** sheet with the files attached. For a single file, its name becomes the note title.

A `.zip` or `.json` file that isn't a recognized Remember backup, Markdown archive, or supported import file is treated as an attachment.

**File limits and batches**

- Each Markdown or text file must be non-empty and under 512 KB.
- Each attachment can be up to 100 MB.
- If any file in a batch can't be imported as a note, the entire batch is offered as attachments instead of being split into separate imports.

### Save to Remember

When you share text, links, web content, images, or files that are treated as attachments, Remember opens the **Save to Remember** sheet over the app you're currently using.

Before saving, you can:

- Edit the **Title** and **Content**. Shared web content keeps its formatting, just like pasted content.
- Add a **Reminder**, **Tags**, and **Importance**.
- Attach a single image or use it as the note's **Banner**. If you share multiple images, they're attached automatically.

Choose what happens next:

- **Save** creates the note and replaces the sheet with a confirmation showing how it will look in your list. Choose **Close** to return to the previous app or **Open note** to jump into the editor.
- **Open in editor** saves the note and takes you directly to the full editor.
- **Cancel**, or swiping the sheet away, discards everything without saving.

### Drag and drop files

On tablets, foldables, ChromeOS, or Android split-screen/multi-window, you can drag files from a file manager or another app onto the unlocked Remember window.

Files are handled like shared files, with these differences:

- Files that can't be imported as notes get attached to a new note. 
- Dropping multiple Markdown or text files, or a Markdown `.zip`, shows how many notes were imported and selects them on the Notes tab.
- File drops are disabled while a note or checklist is in edit mode, so dragged text can go into your content instead. Switch to view mode or return to the notes list to drop files.

### Importing and exporting Markdown

Remember supports `.md`, `.markdown`, and `.txt` files (up to 512 KB each) for notes and checklists.

**Import multiple files**
1.  Tap `+` → **Import**.
2.  Switch to the **Tasks.org & more** tab.
3.  Tap **Choose files** and select `.md`, `.markdown`, or `.txt` files, or a `.zip` archive containing them.

Each file's name, without its extension, becomes the title of the new note or checklist.

**Import into a new note or checklist**
1.  Open a new, empty note or checklist.
2.  Tap the top-right `⋮` menu.
3.  Choose **Import MD** and select a file.

**Export a single note or checklist**

Open a note or checklist with content and tap `⋮` → **Export MD** to save a `.md` file.

**Export multiple notes or checklists**
1.  On the Notes tab, long-press an item to enter multi-select mode.
2.  Select the items you want to export.
3.  Tap **Export** in the bottom selection bar.

Remember creates a `.zip` archive containing a separate `.md` file for each item.

### Troubleshooting sharing and file imports

**Remember isn't listed in Open with**

Open with is available only for a single file with a supported extension: `.md`, `.markdown`, `.txt`, `.zip`, or `.json`. Use **Share** for other file types or multiple files.

**A Markdown or text file won't import**

The file must be non-empty and under 512 KB. Remember displays an error if it can't import the file and returns you to the app you were using.

**An attachment isn't being added**

Files over 100 MB, or files that the sending app doesn't allow Remember to read, are skipped with a message. Try sharing the file again or attaching it from inside a note.

## Organization

### Tags, stars, pins, archive, and trash

These features help you keep your notes organized:

- **Tags** group related notes and lists so you can filter them quickly.
- **Star** marks important items so they're visually distinct.
- **Pin** keeps an item at the very top of the Notes tab.
- **Archive** removes an item from the active list without deleting it.
- **Trash** holds items you intend to remove.

### Pinning

Pinned notes and lists appear in a **Pinned** section at the very top of the Notes tab. This section stays at the top regardless of how you sort or group the list.

Pinning controls placement; starring marks a favorite. They're separate features, and you can use both on the same item.

- Pin or unpin an item from its bottom bar, by swiping a card right (Pin is the first reveal action by default), or by selecting multiple items and choosing **Pin** in the selection bar.
- Searching or filtering can hide a pinned item because those actions narrow the contents of the list. Changing the sort or grouping does not hide it.
- Marking a pinned item done moves it to the **Done** section at the bottom. It stays pinned, so it returns to the top if you mark it not done again.
- Duplicating a pinned item does not pin the copy.

### Sorting and grouping

Use the **Sort** and **Group** chips on the Notes tab to organize notes and checklists.

You can sort or group by dates, title, reminders, and importance. The Sort menu also lets you change the sort direction between **Ascending** and **Descending**.

### Search

Search checks titles, note bodies, tags, and checklist items. It also finds matches in archived and trashed notes.

### Finding archived or trashed notes

Archived and trashed notes are intentionally hidden from the main Notes tab.

Open the **History** tab to review them. Search is also useful when you're looking for a specific item you archived or trashed.

### Marking something done

Marking a note or list done moves it out of your active work and into the **Done** area.

- For a one-time reminder, marking the item done clears its reminder notification.
- For a recurring reminder, marking it done advances it to the next scheduled occurrence. If there is no future occurrence, the item moves to Done like a one-time reminder.

## Reminders

### How reminders work

Add a reminder date and time to any note or list. When the time arrives, Remember posts a notification. Tap the notification to open the item directly.

The reminder sheet's quick chips follow **Preset type** in Settings → Notifications:

- **Timing** offers named times, such as Soon, Later today, and This evening.
- **Duration** offers time spans, such as 30 minutes, 6 hours, 12 hours, and 7 days.

Reminder notifications can include the title, note content or list items, a picture, **Snooze**, **Mark as done**, and one custom action button.

### Importance: Low, Normal, High, and Critical

Importance controls how noticeable a reminder is when it fires. It does not control when it fires.

- **Low:** Silent, with no status bar icon or vibration.
- **Normal:** Uses the standard notification sound and vibration.
- **High:** Appears as a heads-up alert and is treated as an alarm. Use it when a reminder needs to be immediately noticeable.
- **Critical:** Re-alerts every 15 minutes until you snooze it, mark it done, or lower its importance. Overdue items ring together. Swiping the notification away does not stop repeats. Opening the notification shade quiets an active alert early.

Critical reminders have two alert modes:

- **Alarm Mode** (30 seconds, 1 minute, and so on) acts as a full system alarm. It uses your alarm volume and tone, overrides silent and vibrate modes, and passes through Do Not Disturb if DND allows alarms.
- **Chime Mode** (Single chime) plays your High-importance notification sound once per 15-minute alert cycle. It respects silent mode and DND limits.

To switch modes or change the alarm duration, go to Settings → Notifications → **Critical reminder type**. To customize the alarm sound, edit the **Reminders (Critical)** channel in Android System Settings.

### Recurring reminders

Reminders can repeat daily, weekly, monthly, or yearly. You can end a recurrence on a specific date or after a set number of occurrences.

When you mark a recurring reminder done, Remember advances it to the next scheduled occurrence. If there is no future occurrence, the item moves to Done.

### Restore notifications

**Restore notifications** is a Settings option for persistent reminders. When enabled, a dismissed reminder notification comes back immediately if its note or list has not been marked done.

### Pinning notifications

You can manually pin a note or list's reminder notification from its bottom bar.

A manually pinned notification stays active, is automatically restored if dismissed, and returns after your device restarts. It remains pinned until you unpin it or mark the item done.

### Reminder summary

The **reminder summary notification** is a quiet, persistent overview of overdue and upcoming reminders.

It makes no sound and works as a status panel you can glance at without opening Remember.

### Notification permission and reliability

Android requires notification permission before Remember can post reminder alerts, snooze actions, and quick capture notifications.

The Notifications section in Settings shows the permissions available on your device. Some devices also expose a background or battery reliability option. Granting it makes reminders less likely to be delayed by battery optimization. The exact options vary by Android version and manufacturer.

### Snooze

Snooze moves a reminder to a later time without opening the app.

The snooze sheet follows **Preset type** in Settings → Notifications:

- **Timing** offers named times, such as soon, later today, this evening, tomorrow, and next week.
- **Duration** offers 1--60 minutes, 1--24 hours, or 1--30 days. Choose a number and unit, then tap **Snooze**.
- **Pick a specific time** remains available in both modes, with a date and time picker.

### Troubleshooting reminders

**Not getting reminders**

- Check that Android notifications are allowed for Remember.
- Confirm that the note or list still has a reminder set.
- Check whether the item was marked done, archived, or moved to trash.

**A reminder appeared late**

- Android can delay apps because of battery saver or sleep restrictions.
- Go to Settings → Notifications and enable the reliability options shown on your device.
- If your device offers a background or battery exception for Remember, allow it.

## Notification actions

### How notification actions work

Notification actions are buttons you can attach to a reminder notification. They let you act on a reminder directly. For example, a reminder to call someone can show a call button in the notification.

You can add one custom action per note. Reminder notifications always include **Snooze** and **Mark as done**, regardless of whether you add a custom action.

Available actions include:
- Call, message, or email
- Directions
- Open a link, app, or shortcut
- Copy to clipboard
- Share content

Some actions hand off to another app. For example, calls may open the phone app, directions may open a maps app, and links may open a browser. This is expected.

If the target app is missing or the action data is invalid, the action won't run.

### Troubleshooting notification actions

- Check that the action data is valid: phone number, email address, URL, address, app, shortcut, or text.
- Make sure the required app is installed for call, directions, email, and link actions.
- Secret visibility can prevent note content from being used as action data. Check the note's visibility setting.

## Widgets and quick capture

### Available widgets

Remember offers these home-screen widgets:

- **Agenda widget:** Shows overdue and upcoming reminders.
- **Selected notes widget:** Shows notes and lists you choose: all, starred, pinned, or a selected tag.
- **Quick capture widget:** Provides buttons to create a new note or list directly from the home screen.

### Quick capture notification

The **Quick capture notification** is a persistent shortcut for creating a note without opening Remember.

Enable it in Settings. It's useful when you often need to capture something while doing something else.

### Troubleshooting widgets

- Secret notes do not appear in widgets.
- Private notes appear with their content hidden.
- Widgets show active items only; archived and trashed items do not appear.
- The Agenda widget shows reminder items due now or within the next 7 days, not every note.

## Customization

### Appearance

**Theme mode**
Choose **System**, **Light**, **Dark**, or **Black**.

Black uses a true black background rather than dark grey, which can look sharper on OLED screens. It also disables gradient and enhanced shading because those effects are designed for non-black backgrounds.

**Color source**

- **Material You** uses colors from your wallpaper on Android 12 and above. The colors update automatically when your wallpaper changes.
- **Presets** offers eight hand-tuned schemes: Forest, Ember, Grove, Honey, Ocean, Iris, Dusk, and Berry.
- **Custom color** lets you enter any hex value. You can save multiple custom colors and switch between them.

**Palette style**

Palette style controls how Material 3 expands your seed color into a full palette. The same seed color can look quite different depending on the style.

Available styles: Tonal Spot (default), Vibrant, Expressive, Rainbow, Fruit Salad, Neutral, Monochrome, Fidelity, and Content.

Palette style applies to presets and custom colors. Material You manages its own palette.

**Visual effects**

- **Gradient background** blends your primary color into the background for tinted depth rather than a flat surface. Disabled in Black mode.
- **UI scale** makes the whole interface smaller or larger (75% to 125%). Text, icons, and spacing change together.
- **Enhanced shading** makes cards darker in dark mode and lighter in light mode. Disabled in Black mode.
- **Blur bars** adds a frosted-glass effect to the top and bottom bars, allowing content to scroll behind them.

### Notes UI

- **Adaptive note themes** tint the note page with color taken from its cover image.
- **Cover image on cards** displays a note's picture as a soft background on its card, not just inside the note.
- **Note content on cards** displays the note body or list items on the card.

### Defaults

**Defaults** in Settings controls how new notes and lists start, so you don't have to change the same options every time.

- **Default visibility** and **Default importance** apply to every new note and list.
- **Default reminder time** sets the time of day used for new reminders.
- **Default recurrence** sets the repeat rule preselected when you turn on repeat for a reminder.

Changing a default does not change notes and lists you've already created.

### Swipe actions

Remember has two swipe modes.

**Reveal mode** (default)

Swiping a note reveals action buttons instead of immediately performing an action.

Each direction (right and left) has three slots, giving you six configurable actions. Long-press and drag an action to swap its position with another slot.

The default layout is:

- **Right swipe:** Pin, Star, Duplicate
- **Left swipe:** Mark done, Archive, Trash

Reveal mode uses six of the seven available actions. Its editor rearranges the six actions already shown; it doesn't swap in a different action. **Edit** is left out of the default layout because it's already one tap away inside the note. To use Edit as a swipe action, switch to Direct mode.

**Direct mode**

Swiping immediately performs one action, with no intermediate step. You can choose one action for each direction.

This is useful when you want to perform a particular operation as quickly as possible. The defaults are:

- **Right swipe:** Pin
- **Left swipe:** Trash

These are the same as the first actions in the default Reveal mode layout.

**Available actions**

Both modes support Pin, Star, Duplicate, Mark done, Archive, Trash, and Edit.

Haptic feedback at swipe thresholds can be turned on or off independently of the swipe mode.

## Bring your existing data

Moving from another service, or restoring a Remember backup? Start here.

To create a note from a file, link, or share from another app, see **Add content from other apps and files** under Getting started.

### Importing from Google Tasks

Remember can import tasks from Google Tasks in two ways: by signing in directly or by loading a Google Takeout file.

Both methods are strictly read-only. Remember never edits, completes, or deletes anything in Google Tasks.

**Option 1: Connect Google (requires internet)**

1.  On the Notes tab, tap `+` → **Import**.
2.  Open the **Google Tasks** tab and tap **Choose Google account** (or **Continue as ...** if you've signed in before). Grant read-only access.
3.  Once your tasks load, search, filter by task list, and check or uncheck individual tasks.
4.  Choose how to create the tasks in Remember:
    - **One note per task:** Creates a separate note for each task, including its title, notes/details, and due date/reminder.
    - **One note per list:** Combines all selected tasks from each list into a single note.
    - **List as checklist:** Turns each task list into an interactive checklist in Remember.
5.  Tap **Import** to add the selected items.

**Option 2: Google Takeout (no sign-in required; works in the Offline flavor)**

1.  On the **Google Tasks** tab, tap **Open Google Takeout**, tap **Next step**, and then **Create export**.
2.  Check your email for Google's download link, download the archive, and extract it on your device.
3.  Return to Remember's Import screen, tap **Choose JSON file**, and select `Tasks.json` from the extracted Takeout folder.
4.  Review your tasks, choose an import mode (**One note per task**, **One note per list**, or **List as checklist**), and tap **Import**.

### Importing from Tasks.org

1.  In Tasks.org, open **Settings** → **Backup** and create a backup. This saves a `user….json` file to your device.
2.  In Remember, tap `+` → **Import** and switch to the **Tasks.org & more** tab.
3.  Tap **Choose files** and select the saved `user….json` backup file.
4.  Search and filter by list, then choose whether to **Skip imported** or **Overwrite imported**. Select an import mode (**One note per task**, **One note per list**, or **List as checklist**) and tap **Import**.

### Importing or restoring a Remember backup

You can create, import, and restore Remember backup `.zip` files from Settings → **Backup**.

You can also load a backup `.zip` directly from **Tasks.org & more** → **Choose files**, or by dragging and dropping it onto Remember.

**What a backup includes**

Backups include notes, lists, checklist items, tags, reminder details, visibility, importance, icons, actions, archive/trash/done state, attachment metadata, and app settings.

Enable **Include media in backup** to embed pictures and attachment files in the backup. The file will be larger, but images and attachments can then be restored on another device.

**Import vs. restore**

- **Import backup** adds the backup's notes to your existing collection. Importing the same backup again creates duplicates. Use Import when you want to merge data.
- **Restore backup** replaces all current app data with the backup. This is destructive: current notes and lists are wiped before the backup loads. Use Restore when you want to replace your data, not add to it.

**If media is missing after restore**

The backup may not have included media files. If media was stored only as file links, those links may not resolve on a different device. For future backups, enable **Include media in backup**.

### If an import fails

- **Connect Google:** Check your internet connection, or try **Switch account** if sign-in or consent fails.
- **Google Takeout JSON:** Select the `Tasks.json` file from an extracted Google Takeout archive.
- **Tasks.org JSON:** Select the `user….json` backup file created by Tasks.org.
- **Other JSON files:** Remember doesn't import arbitrary JSON files as tasks. When you share or drop an unsupported JSON file, Remember offers to attach it to a new note instead.
- **ZIP files:** A `.zip` that isn't a Remember backup and doesn't contain Markdown files can't be restored. When you share or drop it, Remember offers to attach it to a new note instead.

## Privacy and security

### Where notes are stored

Remember stores notes on your device in a local database. The database is not encrypted; data is stored in plaintext. Android's app sandboxing prevents other apps from accessing it, but the data itself is not encrypted at rest.

Remember supports Android Auto Backup. Your notes, lists, tags, and settings are automatically backed up to your Google account and restored when you reinstall the app on a new device.

Attachments and pictures are not included in Auto Backup. To transfer those too, use Remember's in-app backup with **Include media in backup** enabled.

### Visibility: Normal, Private, and Secret

Visibility controls what appears in notifications and widgets. It is not a sharing or encryption setting.

- **Normal:** The title and note content can appear in notifications and widgets.
- **Private:** Hides notification body content and widget preview text.
- **Secret:** Hides both the note title and body from notifications and keeps the item out of widgets entirely.

### App lock

App lock requires your device lock, or biometrics if your device supports them, to open Remember.

It helps prevent casual access to the app but does not encrypt the underlying data.

## Offline flavor

### What's different in the Offline flavor

The Offline flavor is a separate build of Remember with no internet permission at all. It's available from GitHub Releases as `remember-v<version>-offline.apk`.

Compared with the other versions:

- **No internet permission:** Remember can't connect to the internet.
- **No update checks:** Remember can't check for new versions itself. Use **Track updates via ObtainX** on the Settings page (under Updates on tablets), or manually install a newer offline APK from GitHub Releases.
- **No changelog after an update:** Other versions show what's new the first time you open them after updating.
- **No Connect Google:** Import tasks from a Google Takeout or Tasks.org file instead.
- **Installs alongside the other versions:** The Offline flavor is a separate app with its own notes and settings. To move notes between versions, make a backup in one and restore it in the other. Grant permissions such as notifications again after moving.
