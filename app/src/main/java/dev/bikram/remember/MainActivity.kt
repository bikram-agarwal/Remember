package dev.bikram.remember

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.ui.lock.LockScreen
import dev.bikram.remember.ui.nav.RememberNavGraph
import dev.bikram.remember.ui.theme.RememberTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val ACTION_SHORTCUT_NEW_NOTE = "dev.bikram.remember.action.SHORTCUT_NEW_NOTE"
        const val ACTION_SHORTCUT_NEW_LIST = "dev.bikram.remember.action.SHORTCUT_NEW_LIST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RememberApp).container
        handleIntent(intent)
        setContent {
            val themeState by container.themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val tagColors by container.tagRepository.observeTagColorMap().collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            val interactionState by container.interactionPrefs.state.collectAsStateWithLifecycle(
                initialValue = InteractionState(),
            )
            RememberTheme(
                themeState = themeState.copy(tagColors = tagColors),
                interactionState = interactionState,
            ) {
                AppRoot(container = container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val container = (application as RememberApp).container
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                container.pendingLaunch.value = LaunchAction.NewNote(prefill = text)
            }
            ACTION_SHORTCUT_NEW_NOTE -> {
                container.pendingLaunch.value = LaunchAction.NewNote()
            }
            ACTION_SHORTCUT_NEW_LIST -> {
                container.pendingLaunch.value = LaunchAction.NewList
            }
            Intent.ACTION_VIEW -> {
                val shortcut = intent.getStringExtra("action")
                val openId = intent.getLongExtra("open_note_id", -1L)
                when {
                    shortcut == "new_note" -> container.pendingLaunch.value = LaunchAction.NewNote()
                    shortcut == "new_list" -> container.pendingLaunch.value = LaunchAction.NewList
                    openId > 0L -> container.pendingLaunch.value = LaunchAction.OpenNote(openId)
                }
            }
            else -> {
                val openId = intent.getLongExtra("open_note_id", -1L)
                if (openId > 0L) container.pendingLaunch.value = LaunchAction.OpenNote(openId)
            }
        }
    }
}

@Composable
private fun AppRoot(container: dev.bikram.remember.di.AppContainer) {
    val lockState by container.lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val unlocked by container.appUnlocked.collectAsStateWithLifecycle(initialValue = false)
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val currentLockState = lockState

    if (currentLockState == null) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
        return
    }
    
    if (currentLockState.enabled && !unlocked) {
        LockScreen(
            biometricEnabled = currentLockState.biometric,
            hasPin = currentLockState.hasPin,
            pinLength = currentLockState.pinLength,
            onUnlocked = { container.appUnlocked.value = true },
            verify = { pin -> container.lockPrefs.verify(pin) },
        )
    } else {
        androidx.compose.runtime.CompositionLocalProvider(
            dev.bikram.remember.ui.theme.LocalSnackbarHostState provides snackbarHostState
        ) {
            androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                RememberNavGraph(
                    repository = container.noteRepository,
                    onboardingPrefs = container.onboardingPrefs,
                    themePrefs = container.themePrefs,
                    viewOptionsPrefs = container.viewOptionsPrefs,
                    interactionPrefs = container.interactionPrefs,
                    appScope = container.applicationScope,
                    launchFlow = container.pendingLaunch,
                )
            }
        }
    }
}
