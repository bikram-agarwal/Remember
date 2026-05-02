package dev.bikram.remember.ui.edit
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Intent.EXTRA_SHORTCUT_* constants are deprecated on the SDK; keys are stable for CREATE_SHORTCUT results.
private const val EXTRA_LEGACY_SHORTCUT_INTENT = "android.intent.extra.shortcut.INTENT"
private const val EXTRA_LEGACY_SHORTCUT_NAME = "android.intent.extra.shortcut.NAME"

data class AppChoice(
    val packageName: String,
    val componentName: ComponentName,
    val label: String,
    val icon: Drawable,
)

@Composable
fun AppPickerDialog(
    title: String,
    queryIntent: Intent,
    onPick: (AppChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var items by remember { mutableStateOf<List<AppChoice>?>(null) }

    LaunchedEffect(queryIntent.action) {
        items =
            withContext(Dispatchers.IO) {
                pm
                    .queryIntentActivities(queryIntent, 0)
                    .mapNotNull { info: ResolveInfo ->
                        val ai = info.activityInfo ?: return@mapNotNull null
                        AppChoice(
                            packageName = ai.packageName,
                            componentName = ComponentName(ai.packageName, ai.name),
                            label = info.loadLabel(pm).toString(),
                            icon = info.loadIcon(pm),
                        )
                    }.sortedBy { it.label.lowercase() }
            }
    }

    AppBottomSheet(
        title = title,
        onDismiss = onDismiss,
        scrollable = false,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 520.dp),
        ) {
            val current = items
            when {
                current == null -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                current.isEmpty() -> {
                    Text(
                        stringResource(R.string.app_picker_nothing_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(current, key = { it.componentName.flattenToShortString() }) { app ->
                            AppRow(app = app, onClick = { onPick(app) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppChoice,
    onClick: () -> Unit,
) {
    val bmp =
        remember(app.componentName) {
            try {
                BitmapPainter(app.icon.toBitmap(96, 96).asImageBitmap())
            } catch (_: Throwable) {
                null
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                painter = bmp,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun rememberShortcutPickLauncher(
    onPicked: (intentUri: String, label: String) -> Unit,
): (ComponentName) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val data = result.data ?: return@rememberLauncherForActivityResult
            val intent = IntentCompat.getParcelableExtra(data, EXTRA_LEGACY_SHORTCUT_INTENT, Intent::class.java)
            intent ?: return@rememberLauncherForActivityResult
            val label = data.getStringExtra(EXTRA_LEGACY_SHORTCUT_NAME).orEmpty()
            val uri =
                try {
                    intent.toUri(Intent.URI_INTENT_SCHEME)
                } catch (_: Throwable) {
                    ""
                }
            if (uri.isNotBlank()) onPicked(uri, label)
        }
    return { cn ->
        val launch =
            Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                component = cn
            }
        launcher.launch(launch)
    }
}
