# Remember Help Guide

## Getting started

### Notes vs. lists

Use a **note** when you want free-form text — details, instructions, links, or longer context.
Use a **list** when the work is made of steps you can check off.

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

Each note and list supports: an icon, tags, reminder, notification action, importance level, visibility setting, picture, and attachments. Pictures appear as hero images on cards and in reminder notifications. Attachments stay connected to the note they belong to.

### Note formatting

Notes support Markdown-style writing: headings, bold, italic, underline, strikethrough, lists, quotes, code blocks, links, and dividers. Reminder notifications use a plain-text version of the note so they stay readable.

### Checklists

Lists contain checkable items you can reorder, and supports one level of nesting. Indent an item under a parent to group related subtasks. Checking a parent item checks its children. If every child under a parent becomes checked, the parent checks itself too. Completed items can be separated from active ones.

---

## Organization

### Tags, stars, pins, archive, and trash

- **Tags** group related notes and lists for quick filtering.
- **Star** marks important items so they surface easily. Starred items also feed the Starred widget.
- **Pin** keeps an item at the very top of the Notes tab. See [Pinning](#pinning) below.
- **Archive** removes items from the active list without deleting them.
- **Trash** is for items you intend to remove.

### Pinning

Pinned notes and lists collect in a **Pinned** section at the very top of the Notes tab. The section
stays there no matter how you sort or group the list — pinning is about placement, and starring is
about marking a favorite, so the two are separate and can be used together.

- Pin or unpin from the note's bottom bar, by swiping a card right (Pin is the first reveal action
  by default), or by selecting several notes and using **Pin** in the selection bar.
- Searching or filtering can hide a pinned item, because those narrow down what the list contains.
  Changing the sort or grouping never will. The **Pinned** filter under "Others" does the reverse —
  it shows only your pinned items.
- Marking a pinned item done moves it to the **Done** section at the bottom, like anything else. It
  stays pinned, so it returns to the top if you mark it not done again.
- Duplicating a pinned item does not pin the copy.

### Search

Search checks titles, note bodies, tags, and checklist items. It also finds matches in archived and trashed notes.

### Finding archived or trashed notes

Archived and trashed notes are hidden from the main Notes tab intentionally. Open the **History tab** to review them. Search is also useful when looking for something specific you archived or trashed.

---

## Reminders

### How reminders work

Add a reminder date and time to any note or list. When the time arrives, Remember posts a notification. Tapping it opens the note directly.

Reminder notifications can include the title, note content or list items, a picture, snooze, mark done, and one custom action button.

### High-importance reminders

High-importance reminders use stronger notification behavior: heads-up alerts, sound, and vibration. Use them when a reminder needs to be immediately noticeable. Importance does not affect when a reminder fires — only how loud it is when it does.

### Recurring reminders

Recurring reminders can repeat daily, weekly, monthly, or yearly. You can end the recurrence on a date or after a set number of occurrences.

When you mark a recurring reminder done, Remember advances it to the next scheduled occurrence. If there is no future occurrence, the item moves to done.

### Snooze

Snooze moves a reminder to a later time without opening the app. Presets include soon, later today, this evening, tomorrow, next week, or a custom date and time.

### Keep reminders until done

**Keep reminders until done** is a Settings option for persistent reminders, saving them from being accidentally swiped away. When enabled, a dismissed reminder notification will come back immediately if the note or list is still not marked done.

### Reminder summary

The **reminder summary notification** is a quiet, persistent overview of overdue and upcoming reminders. It makes no sound and works as a status panel you can glance at without opening Remember.

### Notification permission and reliability

Android requires notification permission before Remember can post reminder alerts, snooze actions, and quick capture. The Notifications section in Settings shows the permissions available on your device. Some devices expose a separate background or battery reliability option — granting it makes reminders less likely to be delayed by battery optimization. The exact options vary by Android version and manufacturer.

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
- **Starred widget** — shows starred notes and lists.
- **Quick capture widget** — buttons to create a new note or list directly from the home screen.

### Quick capture notification

The **quick capture notification** is a persistent shortcut for creating a new note without opening the app. Turn it on in Settings. It is useful when you often need to capture something while doing something else.

### Troubleshooting widgets

- Secret notes do not show up in widgets.
- Private notes' content is always hidden in the widget.
- Widgets show active items only — archived and trashed items do not appear.
- The agenda widget shows reminder items due now or within next 7 days, not every note.

---

## Import, backup, and restore

### Importing from Google Tasks

Remember can import tasks from Google Tasks in two ways. Tap the `+` -> Import, and then:

- **Connect Google** to sign into your account to browse and select tasks to import. OR
- **Manual import**, to download `Tasks.json` from Google Takeout and provide that to Remember. No sign-in required.

The import is read-only — Remember never edits your Google Tasks. Imported tasks can become individual notes, grouped notes, or checklist-style lists depending on the import mode you choose.

**If import fails:** check your internet connection for Connect Google, or try switching accounts if sign-in fails. For manual import, select the `Tasks.json` file from the extracted Takeout archive — Remember expects the Google Tasks Takeout format, not a generic JSON file.

### What a backup includes

Backups include notes, lists, checklist items, tags, reminder details, visibility, importance, icons, actions, archive/trash/done state, attachment metadata, and app settings.

Enable **Include media in backup** to embed pictures and attachment files in the backup. This makes the file larger but means images and attachments will restore correctly on another device.

### Import vs. restore

**Import backup** adds the backup's notes to your existing collection. If the same notes already exist, importing again creates duplicates. Use import when you want to merge data.

**Restore backup** replaces all current app data with the backup. It is destructive — current notes and lists are wiped before the backup loads. Use restore when you want to replace your data, not add to it.

**If media is missing after restore:** the backup may not have included media files. If media was stored only as file links, those links may not resolve on a different device. Create future backups with **Include media in backup** enabled.

---

## Privacy and security

### Where notes are stored

Remember stores notes on your device in a local database. The database is not encrypted — data is stored in plaintext. Android's app sandboxing prevents other apps from accessing it, but the data itself is not encrypted at rest.

Remember supports Android Auto Backup. Your notes, lists, tags, and settings are automatically backed up to your Google account and restored when you reinstall the app on a new device. Attachments and pictures are not included in Auto Backup — use the in-app backup with **Include media in backup** enabled if you need those to transfer as well.

### Visibility: Default, Private, and Secret

Visibility controls what appears in notifications and widgets — it is not a sharing or encryption setting.

- **Default** allows the title and note content to appear in notifications and widgets.
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
- **Hero images on cards** — shows a note's picture as a soft background on the card in list view, not just inside the note.

### Swipe actions

**Reveal mode** (default) — swiping a note reveals a row of action buttons rather than immediately doing something. Each direction (right swipe and left swipe) has three slots, giving you six configurable actions in total. You can long-press and drag actions to swap their positions between slots. Default layout: right swipe shows Pin, Star, Duplicate; left swipe shows Mark done, Archive, Trash.

Reveal mode holds six of the seven available actions, and its editor rearranges the six that are there rather than swapping in a different one. Edit is the action left out of the default layout — it is one tap away inside the note anyway. To use Edit on swipe, choose **Direct mode**, where every action is selectable.

**Direct mode** — swiping immediately executes a single action with no intermediate step. One action per direction. Useful if you have a single operation you want to do as fast as possible. Defaults: swipe right pins, swipe left moves to trash — the same as each direction's first reveal action.

Available actions for either mode: Pin, Star, Duplicate, Mark done, Archive, Trash, Edit.

Haptic feedback at swipe thresholds can be turned on or off independently of the mode.
