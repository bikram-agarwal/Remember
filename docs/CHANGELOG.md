## v1.5.0 Title sort, selected notes widget

### ✨ New Features
- **Selected notes widget**: Display notes and lists on your home screen by category - choose from all notes, starred, pinned, or any specific tag. Multiple widgets can be added to your home screen with independent filters.
- **In-widget filter switcher**: Switch between all, starred, pinned, or tag collections at any time by tapping the new settings gear icon directly in the widget header.

## 🛠 Improved Features
- **Alphabetical sorting**: Sort notes and lists based on Title, A to Z or Z to A.

---

## v1.4.2 Bulk expand/collapse, UI Scale

### ✨ New Features
- Expand or collapse every note group at once from the home toolbar. 
- UI scale in Appearance settings (75% to 125%). Make the whole interface - Text, icons, and spacing - smaller or larger.

### 🛠 Improved Features
- Reminder notifications now display nested checklist items indented under their parent items, preserving the checklist structure.
- Reminder notifications for notes without body text now appear cleanly without generic placeholder text.

### 🐛 Bug Fixes
- Fixed an issue where tapping a note from a home screen widget or reminder notification could fail to open the note and leave the app on the notes list.
- Fixed a visual glitch in dual-pane landscape mode where switching tabs could cause note cards to slide across panes into the editor.
- Several UI fixes for landscape / tablet layout, gesture vs 3-button navigation mode etc. 

---

## v1.3.2 Horizontal line

### ✨ New Features
- Markdown in notes now support rendering `---` as a horizontal line / divider. 

### 🛠 Improved Features
- When there are no overdue reminders, the "Overdue" section is hidden in the homescreen widget, giving more space to the "Upcoming" reminders.
- Scaled down and optically centered the "New note" button icon.
- "Done" button icon and colors are tweaked slightly for better visual balance.

---

## v1.3.1 Pinned notes

### ✨ New Features
- Pin notes to keep them at the top of Home, no matter how you sort or group. 
    - Filter by pinned, pin from the editor or with a swipe, and pin several notes at once from multi-select.
- Star several notes at once from multi-select. Pin and Star stay disabled when everything you selected is already pinned or starred.
- Turn haptic feedback on or off in Settings.

### 🐛 Bug Fixes
- Swiping to unpin, unstar, or reopen works again (i.e. it no longer gets stuck after pin, star, done).
- Fixed a bug where taps were acting like long-press on high density screen phones. 

---

## v1.2.4: Faster same day reminders

### 🛠 Improved Features
- Setting same day reminder is now faster — choose a time and tap Save. Current date is automatically selected.

### 🐛 Bug Fixes
- Removed the edit button from the checklist action toolbar. Tapping anywhere on the body already puts it in edit mode. 

---

## v1.2.2: Reminder presets, check/uncheck all, fixes

### ✨ New Features
- Added one-tap presets for quickly choosing common reminder times.
- Long press any checkbox in notes or checklist to quickly "Check all" or "Uncheck all"

### 🛠 Improved Features
- Backups use less memory and reject unsafe or excessively large archives.
- Cloud backups now use persistent folders and show clearer provider and folder names.
- Restoring settings now warns when backup folders need to be selected again.
- App lock now allows a five-minute grace period when briefly leaving the app.
- Icon and app pickers load more smoothly in the background.
- Directional icons now display correctly in right-to-left languages.
- Material You controls retain better visual separation from card backgrounds.
- Update downloads use safer filenames and clearer in-app feedback.

### 🐛 Bug Fixes
- Fixed archived notes being missing from backups.
- Failed imports no longer leave partially restored notes behind.
- Recurring reminders retain their original schedule after snoozing and restoring a backup.
- Snooze no longer reports success before the change is saved.
- External star, archive, trash, and completion changes no longer overwrite unsaved edits.
- Duplicated and newly attached checklists retain their hierarchy, details, and completion state.
- Two-pane Select all no longer includes notes hidden inside collapsed sections.
- Update checks and background maintenance tasks no longer retry indefinitely.

---

## v1.2.0: Custom fonts, AMOLED toggle

### ✨ New Features
- Import your own font (`.ttf` or `.otf`) from Settings → Appearance and use it across the whole app.

### 🛠 Improved Features
- Pure black (OLED) is now a separate toggle under System or Dark, instead of a fourth theme mode in the picker.

---

## v1.1.9: Quick Tiles, Wearable notifications

### ✨ New Features
- **Quick Settings tiles** — add "New note" and "New list" tiles to your Quick Settings panel to jump straight into creating one.
- **Notifications to wearables** — Reminder notifications can now be mirrored to connected watches/wearables too.

### 🛠 Improved Features
- Long **checklist items** now wrap onto multiple lines instead of being cut off at one line.
- **Big text / large display sizes** are handled far better across the app: settings backup buttons, note timestamps, and the update/notification banners now reflow or resize instead of overlapping or being cut off.
- **Reminder date & time pickers** now fit properly in landscape and at large text sizes.
- **Bottom sheets** are more compact in landscape, and while you're typing the title and buttons step out of the way so the keyboard no longer covers the field.
- An **"unsaved changes" prompt** now guards your edits before discarding — across reminders, tags, bulk tagging, notification actions, and note behavior.

### 🐛 Bug Fixes
- Fixed a bug where editing any item in checklist would make the cursor jump to the end of line. 
- Fixed a bug where collapsing and expanding sections in Notes tab was crashing the app. 

---

## v1.1.7: Image compression, markdown improvements

### ✨ New Features
- Added a "Compress images" setting (on by default)
    - Downscales and compresses new photo attachments and cover photos. 
    - Metadata like GPS location, camera details, and photo date/ratings is also removed from compressed images. 
    - Existing photos and non-image files aren't affected,
    - This can't be undone for a photo once it's been compressed. 
    - Animated GIFs/WebPs are left untouched so they don't lose their animation, and a photo is only compressed if doing so actually makes it smaller.
- After the app updates, it now opens the changelog for you automatically.

### 🐛 Bug Fixes
- Fixed the app freezing or lagging when pasting or typing a large amount of text into a note.
- Improved markdown live preview and on-save render. 
- The onboarding permissions screen background now blends with the app's theme instead of always showing a solid color.

---

## v1.1.6: Snooze improvements

### 🐛 Bug Fixes
- **Preserve Recurring Schedules:** Snoozing a recurring reminder no longer overwrites its original baseline time with the snooze time.
- **Snooze Status Visibility:** Added a dedicated indicator to the reminder row and sheet to show exactly when a snoozed alert is pending.

### 📦 Others
- First F-Droid version in release.

---

## v1.1.4: Hourly reminders, widget improvements

### 🛠 Improved Features
- **Open Remember from links.** The app now responds to `remember://` links, so deep-links work properly.
- **Quick Capture widget reads better at small sizes.** On narrow widgets the header and buttons now use shorter labels so text no longer overflows or wraps.
- **Tidier reminder notifications.** A note with multiple due reminders now shows a single notification instead of one per reminder.
- **More detailed diagnostics report**, including display/font-scale info and a summary of your installed widgets — handy when reporting an issue.

### 📦 Others
- GitHub releases are now build-attested using GitHub Actions, for better supply chain security and your peace of mind.

---

## v1.1.2: Hourly reminders, widget improvements

### ✨ New Features
- **Hourly reminders.** New option to set hourly reminders for a note.

### 🛠 Improved Features
- **Widgets now cap font scaling.** If the device is using huge display / font size, the widget will now cap the font scaling to prevent text from overflowing the widget bounds.
- **Reduced app size** by removing unnecessary app bundles, making the app lighter and more efficient.

---

## v1.1.1: Landscape / tablet layout, grid view, multi-reminders

### ✨ New Features
- **Landscape and tablet support.** The app now adapts to landscape and larger screens — notes, history, and settings can show a side-by-side list-and-detail view.
- **List and Grid views.** The app now also offers a two-column mosaic layout for the notes.
- **Multiple reminders per note.** Add up to three reminders to a single note, each with its own time and repeat schedule.

### 🛠 Improved Features
- **More complete backups.** Backups now also save your reminder settings, quick-capture settings, update preferences, and all reminders on each note.
- **Clearer, consistent confirmation dialogs** across delete, clear, and unsaved-changes prompts, with destructive actions clearly marked.
- **More detailed diagnostics report**, including your settings, storage space, and permission/alarm status — useful when reporting a problem.
- **Smoother background shading** with a finer intensity range.
- **Remember collapsed state** of different section in settings page.

### 🐛 Bug Fixes
- Background update checks no longer crash and retry safely when the network is unavailable.
- Fixed a bug that was preventing list items from being drag-n-dropped for reordering. 

---

## v1.0.4: Checklist item notes, time format

### ✨ New features
- Add notes to individual checklist items.
- Imported Google Tasks lists now keep task notes with their matching checklist items.
- `Created` and `Updated` timestamps are now shown at the bottom of every note / checklist.

### 🧾 Misc
- Backups and restores now preserve checklist item notes.
- Existing checklist data upgrades cleanly with support for item notes.
- App now follows system time format (12 hours / 24 hours)

---

## v1.0.2: Launch of **Remember**

### ✨ Features
- **Notes and lists**: Rich Markdown notes and nested checklists, both with reminders, tags, attachments, pictures, and notification actions.
- **Reminders that stick**: Re-posts dismissed reminder notifications until the note is marked done — accidental swipes don't make you miss things.
- **Notification actions**: Add a custom action button (call, message, email, directions, open link, open app, copy, share) directly to any reminder notification.
- **Recurring reminders**: Daily, weekly, monthly, yearly — plus calendar-grade patterns like "the last Friday of every month," with an end date or occurrence count.
- **Snooze and high-importance**: Human-readable snooze presets; opt-in heads-up alerts, system alarm level reminders.
- **Organise**: Tags, star, archive, trash, full-text search (including archived and trashed), multi-select bulk actions, and a History tab.
- **Widgets and quick capture**: Agenda, Starred, and Quick capture home-screen widgets; persistent quick-capture notification shortcut.
- **Google Tasks import**: Import from a connected account or a Takeout JSON file — as individual notes, grouped notes, or native checklists.
- **Backup**: ZIP export with optional embedded media, cloud folder support (Google Drive, Dropbox, etc. via SAF), and auto-export on change or on a daily schedule.
- **Theme engine**: Material You, eight color presets, custom hex, nine palette styles, gradient, forsted blur, enhanced shading, black OLED mode, and hero images on cards.
