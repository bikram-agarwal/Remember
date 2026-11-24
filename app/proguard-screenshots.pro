# Keep rules for running the screenshot suite against the release-type devRelease build.
# Applied ONLY when building with -PscreenshotsOnDevRelease=true (../capture_screenshots.ps1),
# so normal devRelease/release outputs are unchanged. The androidTest code seeds demo data
# through these app classes, which R8 would otherwise strip or rename.
# Regenerate after changing the screenshot tests: list the app classes they reference with
#   jdeps -verbose:class <androidTest classes>/dev/bikram/remember/screenshots

-keep class dev.bikram.remember.MainActivity { *; }
-keep class dev.bikram.remember.data.ActionType { *; }
-keep class dev.bikram.remember.data.BackupPrefs { *; }
-keep class dev.bikram.remember.data.ColorSource { *; }
-keep class dev.bikram.remember.data.DefaultNotePrefs { *; }
-keep class dev.bikram.remember.data.DevModePrefs { *; }
-keep class dev.bikram.remember.data.GroupBy { *; }
-keep class dev.bikram.remember.data.IconPickerPrefs { *; }
-keep class dev.bikram.remember.data.Importance { *; }
-keep class dev.bikram.remember.data.InteractionPrefs { *; }
-keep class dev.bikram.remember.data.LockPrefs { *; }
-keep class dev.bikram.remember.data.MonthlyMode { *; }
-keep class dev.bikram.remember.data.MonthlyMode$ByNthWeekday { *; }
-keep class dev.bikram.remember.data.NoteAction { *; }
-keep class dev.bikram.remember.data.NoteLayoutMode { *; }
-keep class dev.bikram.remember.data.NoteOptions { *; }
-keep class dev.bikram.remember.data.NoteReminder { *; }
-keep class dev.bikram.remember.data.NoteRepository { *; }
-keep class dev.bikram.remember.data.NotesUiPrefs { *; }
-keep class dev.bikram.remember.data.OnboardingPrefs { *; }
-keep class dev.bikram.remember.data.PaletteStyleOpt { *; }
-keep class dev.bikram.remember.data.PersistableChecklistItem { *; }
-keep class dev.bikram.remember.data.QuickCapturePrefs { *; }
-keep class dev.bikram.remember.data.RecurrenceEndKind { *; }
-keep class dev.bikram.remember.data.RecurrenceRule { *; }
-keep class dev.bikram.remember.data.RecurrenceUnit { *; }
-keep class dev.bikram.remember.data.ReminderPrefs { *; }
-keep class dev.bikram.remember.data.SortDir { *; }
-keep class dev.bikram.remember.data.SortKey { *; }
-keep class dev.bikram.remember.data.TagRepository { *; }
-keep class dev.bikram.remember.data.ThemeMode { *; }
-keep class dev.bikram.remember.data.ThemePrefs { *; }
-keep class dev.bikram.remember.data.UpdateCheckSchedule { *; }
-keep class dev.bikram.remember.data.UpdatePrefs { *; }
-keep class dev.bikram.remember.data.ViewOptions { *; }
-keep class dev.bikram.remember.data.ViewOptionsPrefs { *; }
-keep class dev.bikram.remember.data.Visibility { *; }
-keep class dev.bikram.remember.di.DevOptionsDependenciesEntryPoint { *; }

# The Hilt component implementing the entry point the tests use.
-keep class * implements dev.bikram.remember.di.DevOptionsDependenciesEntryPoint { *; }

# Libraries the androidTest APK shares with the app. At runtime the test uses the app APK's copy,
# so anything R8 strips from the app (e.g. androidx.tracing, which only the test runner needs)
# fails with NoClassDefFoundError / NoSuchMethodError. Derived from the test APK's references
# (Compose UI test, coroutines test, AndroidX Test, Room migration tests); kept by package so
# edits to the tests don't need new rules.
-keep class androidx.activity.** { *; }
-keep class androidx.arch.core.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.concurrent.futures.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class androidx.tracing.** { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
