# Add project specific ProGuard rules here.
-keep class dev.bikram.remember.widget.** extends androidx.glance.appwidget.action.ActionCallback { *; }
-keep class dev.bikram.remember.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class dev.bikram.remember.widget.** extends androidx.glance.appwidget.GlanceAppWidget { *; }
