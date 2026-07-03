## v1.1.7: Image compression, markdown improvements

### ✨ New Features
- Added a "Compress images" setting (on by default)
    - Downscales and compresses new photo attachments and cover photos. 
    - Metadata like GPS location, camera details, and photo date/ratings is also removed from compressed images. 
    - Existing photos and non-image files aren't affected,
    - This can't be undone for a photo once it's been compressed. 
    - Animated GIFs/WebPs are left untouched so they don't lose their animation, and a photo is only compressed if doing so actually makes it smaller.
- On first launch after app update, the changelog sheet is automatically shown.

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
