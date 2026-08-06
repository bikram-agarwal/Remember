# Backup, Import, and Restore

---

## Hero Photos and Attachments

### Live App Storage

Both photos and attachments are copied into app-private storage when they are added to a note.

**Hero photos** are stored under `/data/user/0/<package-id>/files/note_heroes/cover_<timestamp>.jpg` (also reachable via the `/data/data/<package-id>/files/` symlink). On success the note stores a FileProvider `content://` URI for the private copy. If the copy fails, the note falls back to the original picked URI and tries to persist SAF read permission.

Remember does not store separate masked and full hero-photo files. A note stores one image URI plus optional framing metadata:

- `pictureUri` — the single image used by both the inline masked hero and the full-screen viewer.
- `pictureHeroFraming` — the user-selected crop stored as JSON: focal point and zoom. The inline hero applies this into a 16:9 mask at render time; the full-screen viewer shows the full image.

**Attachments** are stored under `/data/user/0/<package-id>/files/note_attachments/<noteId>/<timestamp>_<displayName>`. On success the attachment row stores a FileProvider `content://` URI for the private copy. If the copy fails, the row falls back to the original URI and tries to persist SAF read permission. Three fields are stored per attachment:

- `uri`
- `displayName`
- `mimeType`

### In Backup

When **Include media in backup** is enabled, the backup ZIP embeds the actual bytes:

- Hero photos are copied to `media/<noteId>/picture.<ext>`.
- Attachments are copied to `media/<noteId>/att_<index>.<ext>`.
- The matching `pictureUri` or attachment `uri` in `notes.json` is replaced with a `REL:` path pointing at the ZIP entry.

When **Include media in backup** is disabled, the backup stores the URI strings but not the bytes. Those references may not resolve on another device or after the original file moves.

The backup manifest tracks four counts: `mediaReferenceCount`, `mediaEmbeddedCount`, `mediaLinkedCount`, and `mediaFailedCount`. A failed embed (e.g., permission revoked, source deleted) does not abort the backup — the remaining notes are still exported.

### On Import and Restore

`REL:` media entries in the ZIP are extracted, copied into internal app storage under `/data/user/0/<package-id>/files/remember_backup/<newNoteId>/`, exposed through FileProvider URIs, and written back to the restored note or attachment rows.

External `content://` URIs are preserved as-is. If the source provider or file no longer exists on the destination device, the media reference is kept in the database but the media is inaccessible.

---

## Backup

### What's Included

Every manual or scheduled backup is a ZIP file containing `notes.json` and `settings.json`, plus a `media/` directory when media embedding is on.

**`notes.json` includes:**

- All notes and lists — active, archived, and trashed
- Per note: title, body, kind (NOTE / LIST), color index, starred, pinned, archived, trashed, locked, icon key, importance, visibility, timestamps (created, updated, trashed, completed, pinned)
- Reminders: the scheduled timestamp and full recurrence rule (serialized JSON)
- Notification actions (all 10 action types, with title, details, and extra)
- Tags assigned to each note
- Checklist items: text, checked state, sort order, parent/child hierarchy (depth 0 or 1)
- Attachment metadata: URI, display name, MIME type
- Hero photo URI and framing JSON
- Tag color map (tag name → hex color) for all tags in the database

**`settings.json` includes:**

- Theme preferences
- View options preferences
- Lock preferences (PIN/biometric config)
- Interaction preferences (swipe actions, haptics)
- Backup preferences (configured folder URIs, toggle states)

**Not included:**

- Widget data
- Notification display state
- Cached or temporary files
- Google Tasks sync metadata

### File Format

```
remember_backup_YYYYMMDD_HHmm.zip
├── backup_manifest.json
├── notes.json
├── settings.json
└── media/
    ├── {noteId}/
    │   ├── picture.{ext}
    │   ├── att_0.{ext}
    │   └── att_1.{ext}
    └── {nextNoteId}/
        └── ...
```

`notes.json` uses schema version 2. Version 1 (legacy) is still supported on import. Media URIs starting with `REL:` are relative paths inside the ZIP; all others are external device URIs.

### Export Destinations

Backup folders are selected via the Storage Access Framework. Both a local folder (internal storage, SD card) and a cloud folder (Google Drive, OneDrive, Dropbox via SAF) can be configured at the same time. Each configured folder receives a copy of the ZIP on every export.

The filename format is `remember_backup_YYYYMMDD_HHmm.zip`. Old backups in the folder are not automatically deleted.

### Auto-Export

Two automatic export paths are available in addition to manual export:

- **Export on change** — writes a new backup when the app exits after any note change (debounced).
- **Scheduled export** — a daily WorkManager job that runs once per day with a battery-low constraint. Runs only when at least one export folder is configured.

Both use the same ZIP format and folder targets as manual export.

---

## Import (Merge)

Import reads a backup ZIP and adds its notes to the existing database without removing anything first.

- Notes are inserted with new IDs. Importing the same backup file twice creates duplicates.
- Settings from the backup (theme, view options, lock, interaction, backup prefs) are overwritten onto the current settings.
- Tag colors from the backup are merged; existing tag colors for matching tag names are overwritten.
- Embedded media (`REL:` entries) is extracted and copied into app storage. External URIs are preserved as-is.
- Reminder scheduling is suppressed during import to avoid triggering duplicate notifications. Reminders are re-evaluated after import completes.
- Notes are inserted individually. If one fails, the others still proceed — partial import is possible.
- Returns the count of notes added.

---

## Restore (Replace)

Restore replaces the entire database with the backup contents.

- All existing notes (active, archived, trashed) are deleted first.
- All existing reminder schedules are cancelled before deletion.
- Notes from the backup are inserted with their original IDs preserved.
- The entire operation runs inside a Room transaction. If it fails at any point, the database rolls back to its pre-restore state — the original notes are intact.
- Settings, tag colors, and media are handled the same way as Import.
- After a successful restore, reminder schedules are rebuilt from the restored notes.
- Returns the count of notes restored.

---

## Google Tasks Import

Google Tasks can be imported two ways: by signing in with a Google account (reads live task data via OAuth) or by providing a `Tasks.json` file from a Google Takeout export (no sign-in required). The import is read-only — Remember never writes back to Google Tasks.

### What's Imported

| Google Tasks field | Remember equivalent |
| --- | --- |
| Title | Note title (or first line of notes if title is blank) |
| Notes / description | Note body |
| Due date | Reminder at 09:00 local time on that date |
| Completed status | `completedAt` timestamp |
| Task list title | Tag |
| Subtask hierarchy | Preserved depending on mode (see below) |

### What's Not Imported

- Reminder time (Takeout exports only expose date, not time)
- Task attachments (not exposed by the Google Tasks API)
- Recurrence rules with precise timing
- Custom colors

### Import Modes

**One note per task** — each task becomes its own note. Subtasks become separate notes; the parent relationship is not preserved. Use this for a flat migration where tasks should remain independent.

**Group by list** — all tasks from one Google list become a single note. The body is a Markdown checklist with subtasks indented by two spaces. Use this for a compact, readable summary of a list.

**List as checklist** — all tasks from one Google list become a single Remember checklist (LIST kind). Subtasks become depth-1 children in the native checklist structure. Use this for an interactive checklist that matches Remember's hierarchy.

### Recurring Tasks

Takeout exports contain multiple instances of recurring tasks. The parser collapses them into a single representative task based on `task_recurrence_id` (or title + due date similarity as a fallback). Recurrence rules are not imported.

### Idempotency

Already-imported tasks can be skipped by passing a map of `googleTaskId → rememberNoteId`. If `overwrite` is true, the existing note is updated in place instead of skipped.

---

## Failure Modes

| Situation | What happens |
| --- | --- |
| Output stream can't be opened for export | Export returns -1; nothing is written |
| ZIP write fails mid-export | Partial ZIP may exist; error logged to DiagnosticLog |
| Media embed fails for one note | That note's media is skipped; the rest of the export continues |
| ZIP is corrupted or unreadable on import | Import aborts; existing notes are untouched |
| `notes.json` is missing or invalid | Import aborts; existing notes are untouched |
| Media extraction fails for one note during import | That note's media is null; the note itself is still imported |
| Full restore fails mid-transaction | Room rolls back; original database state is preserved |
| External media URI is invalid on destination device | URI is stored but the media is inaccessible; note is otherwise intact |
