package dev.bikram.remember.update

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.worker.UpdateCheckWorker
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckWorkScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val updatePrefs: UpdatePrefs,
    ) {
        suspend fun syncFromPreferences() {
            val schedule = updatePrefs.snapshot().updateCheckSchedule
            when (schedule) {
                UpdateCheckSchedule.DAILY_AT_21 -> enqueueOneTime(delayMillis = millisUntilNextDailyNinePm())
                UpdateCheckSchedule.WEEKLY_MONDAY_AT_21 -> enqueueOneTime(delayMillis = millisUntilNextMondayNinePm())
                UpdateCheckSchedule.AT_APP_START, UpdateCheckSchedule.NEVER -> cancel()
            }
        }

        private fun enqueueOneTime(delayMillis: Long) {
            val safeDelay = delayMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
            val request =
                OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setInitialDelay(safeDelay, TimeUnit.MILLISECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UpdateCheckWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UpdateCheckWorker.UNIQUE_WORK_NAME)
        }

        private fun millisUntilNextDailyNinePm(): Long {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            var target =
                now
                    .withHour(21)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis()
        }

        private fun millisUntilNextMondayNinePm(): Long {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            var candidate =
                now
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                    .withHour(21)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusWeeks(1)
            }
            return Duration.between(now, candidate).toMillis()
        }

        companion object {
            fun supportsSilentChecks(): Boolean = !BuildConfig.USE_PLAY_IN_APP_UPDATES
        }
    }
