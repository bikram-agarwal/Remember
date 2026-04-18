package dev.bikram.remember.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.bikram.remember.RememberApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RememberApp
        val scheduler = app.container.reminderScheduler
        val now = System.currentTimeMillis()
        runBlocking {
            val all = app.container.noteRepository.observeActive().first()
            all.forEach { item ->
                val at = item.note.reminderAt ?: return@forEach
                if (at > now) scheduler.schedule(item.note.id, at)
            }
        }
    }
}
