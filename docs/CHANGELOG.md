## v1.1.0: Landscape / tablet layout, grid view, multi-reminders

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
