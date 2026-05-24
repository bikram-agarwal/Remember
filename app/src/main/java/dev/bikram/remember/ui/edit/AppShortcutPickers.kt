package dev.bikram.remember.ui.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Intent.EXTRA_SHORTCUT_* constants are deprecated on the SDK; keys are stable for CREATE_SHORTCUT results.
private const val EXTRA_LEGACY_SHORTCUT_INTENT = "android.intent.extra.shortcut.INTENT"
private const val EXTRA_LEGACY_SHORTCUT_NAME = "android.intent.extra.shortcut.NAME"
private const val EXTRA_LEGACY_SHORTCUT_ICON = "android.intent.extra.shortcut.ICON"
private const val EXTRA_LEGACY_SHORTCUT_ICON_RESOURCE = "android.intent.extra.shortcut.ICON_RESOURCE"

data class AppChoice(
    val packageName: String,
    val componentName: ComponentName,
    val label: String,
    val icon: Drawable,
)

data class ShortcutPick(
    val intentUri: String,
    val label: String,
    val icon: Drawable?,
)

@Composable
fun AppPickerDialog(
    title: String,
    queryIntent: Intent,
    onPick: (AppChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = title,
        onDismiss = onDismiss,
        scrollable = false,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        AppPickerContent(
            queryIntent = queryIntent,
            onPick = onPick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPickerContent(
    queryIntent: Intent,
    onPick: (AppChoice) -> Unit,
    modifier: Modifier = Modifier,
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

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 520.dp),
    ) {
        val current = items
        when {
            current == null -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
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
    onPicked: (ShortcutPick) -> Unit,
): (ComponentName) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val data = result.data ?: return@rememberLauncherForActivityResult
            val intent = IntentCompat.getParcelableExtra(data, EXTRA_LEGACY_SHORTCUT_INTENT, Intent::class.java)
            intent ?: return@rememberLauncherForActivityResult
            val label = data.getStringExtra(EXTRA_LEGACY_SHORTCUT_NAME).orEmpty()
            val icon = shortcutIcon(data, resources, context.packageManager)
            val uri =
                try {
                    intent.toUri(Intent.URI_INTENT_SCHEME)
                } catch (_: Throwable) {
                    ""
                }
            if (uri.isNotBlank()) {
                onPicked(
                    ShortcutPick(
                        intentUri = uri,
                        label = label,
                        icon = icon,
                    ),
                )
            }
        }
    return { cn ->
        val launch =
            Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                component = cn
            }
        launcher.launch(launch)
    }
}

@SuppressLint("DiscouragedApi")
private fun shortcutIcon(
    data: Intent,
    resources: android.content.res.Resources,
    packageManager: android.content.pm.PackageManager,
): Drawable? {
    val bitmapIcon = IntentCompat.getParcelableExtra(data, EXTRA_LEGACY_SHORTCUT_ICON, Bitmap::class.java)
    if (bitmapIcon != null) return bitmapIcon.toDrawable(resources)

    val iconResource =
        IntentCompat.getParcelableExtra(
            data,
            EXTRA_LEGACY_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource::class.java,
        ) ?: return null
    return runCatching {
        val appResources = packageManager.getResourcesForApplication(iconResource.packageName)
        val iconId = appResources.getIdentifier(iconResource.resourceName, null, null)
        if (iconId == 0) {
            null
        } else {
            ResourcesCompat.getDrawable(appResources, iconId, null)
        }
    }.getOrNull()
}
