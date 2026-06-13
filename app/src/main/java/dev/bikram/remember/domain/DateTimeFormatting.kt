package dev.bikram.remember.domain

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Wall-clock time-of-day (hour:minute) formatted with the system 12/24-hour setting and the
 * device locale — including a *localized* AM/PM marker. Shared by every screen that renders a
 * time-of-day (the reminder picker pill, the options panel created/updated/reminder lines) so
 * none of them hand-roll the format (the old inline versions risked hardcoded English "AM"/"PM"
 * or a forced "%02d:%02d").
 *
 * Mirrors FilePipe's `dev.bikram.filepipe.domain.formatTimeOfDay` so the two apps stay in lockstep.
 */
fun formatTimeOfDay(
    context: Context,
    hour: Int,
    minute: Int,
): String {
    val calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/**
 * Convenience overload that formats the time-of-day component of an epoch-millis instant in the
 * device's default time zone, using the system 12/24-hour setting and locale. Equivalent to
 * `DateFormat.getTimeFormat(context).format(Date(epochMillis))`.
 */
fun formatTimeOfDay(
    context: Context,
    epochMillis: Long,
): String = DateFormat.getTimeFormat(context).format(Date(epochMillis))

/**
 * Stable, sortable, ASCII timestamp for backup file names. Intentionally locale-independent
 * (fixed pattern + [Locale.US]) so exported names stay portable and chronologically ordered
 * regardless of device locale or 12h/24h setting.
 *
 * Mirrors FilePipe's `dev.bikram.filepipe.domain.backupFileTimestamp`.
 */
fun backupFileTimestamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
