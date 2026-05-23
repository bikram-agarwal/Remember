package dev.bikram.remember

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.OnboardingState
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.ui.InAppRatingAutoPromptHost
import dev.bikram.remember.ui.components.UpdateChromeState
import dev.bikram.remember.ui.lock.LockScreen
import dev.bikram.remember.ui.nav.RememberNavGraph
import dev.bikram.remember.ui.tags.LocalTagColors
import dev.bikram.remember.ui.theme.RememberTheme
import dev.bikram.remember.update.AppReviewLauncher
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.notificationDedupeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var tagRepository: TagRepository

    @Inject lateinit var themePrefs: ThemePrefs

    @Inject lateinit var interactionPrefs: InteractionPrefs

    @Inject lateinit var onboardingPrefs: OnboardingPrefs

    @Inject lateinit var lockPrefs: LockPrefs

    @Inject lateinit var updatePrefs: UpdatePrefs

    @Inject lateinit var rememberUpdateState: RememberUpdateState

    @Inject lateinit var appReviewLauncher: AppReviewLauncher

    @Inject lateinit var playInAppUpdateProgressController: PlayInAppUpdateProgressController

    @Inject lateinit var pendingLaunch: MutableStateFlow<LaunchAction?>

    @Inject lateinit var appUnlocked: MutableStateFlow<Boolean>

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    companion object {
        const val ACTION_SHORTCUT_NEW_NOTE = "dev.bikram.remember.action.SHORTCUT_NEW_NOTE"
        const val ACTION_SHORTCUT_NEW_LIST = "dev.bikram.remember.action.SHORTCUT_NEW_LIST"
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
        const val EXTRA_OPEN_NOTE_EXIT_ON_BACK = "open_note_exit_on_back"
        const val EXTRA_OPEN_SETTINGS_UPDATES = "extra_open_settings_updates"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        handleIntent(intent)
        applicationScope.launch { themePrefs.migrateLegacyColorSourceIfNeeded() }
        setContent {
            val themeState by themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (themeState.themeMode) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.BLACK -> true
                }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
            }
            val tagColors by tagRepository.observeTagColorMap().collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            CompositionLocalProvider(LocalTagColors provides tagColors) {
                RememberTheme(
                    themeState = themeState,
                ) {
                    AppRoot(
                        noteRepository = noteRepository,
                        onboardingPrefs = onboardingPrefs,
                        interactionPrefs = interactionPrefs,
                        lockPrefs = lockPrefs,
                        updatePrefs = updatePrefs,
                        rememberUpdateState = rememberUpdateState,
                        appReviewLauncher = appReviewLauncher,
                        playInAppUpdateProgressController = playInAppUpdateProgressController,
                        appScope = applicationScope,
                        launchFlow = pendingLaunch,
                        appUnlocked = appUnlocked,
                    )
                }
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
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS_UPDATES, false)) {
            pendingLaunch.value = LaunchAction.OpenSettingsUpdates
            intent.removeExtra(EXTRA_OPEN_SETTINGS_UPDATES)
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                pendingLaunch.value = LaunchAction.NewNote(prefill = text)
            }
            ACTION_SHORTCUT_NEW_NOTE -> {
                pendingLaunch.value = LaunchAction.NewNote()
            }
            ACTION_SHORTCUT_NEW_LIST -> {
                pendingLaunch.value = LaunchAction.NewList
            }
            Intent.ACTION_VIEW -> {
                val shortcut = intent.getStringExtra("action")
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val exitOnBack = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXIT_ON_BACK, false)
                when {
                    shortcut == "new_note" -> pendingLaunch.value = LaunchAction.NewNote()
                    shortcut == "new_list" -> pendingLaunch.value = LaunchAction.NewList
                    openId > 0L -> pendingLaunch.value = LaunchAction.OpenNote(openId, exitOnBack)
                }
            }
            else -> {
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val exitOnBack = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXIT_ON_BACK, false)
                if (openId > 0L) pendingLaunch.value = LaunchAction.OpenNote(openId, exitOnBack)
            }
        }
    }
}

@Composable
private fun AppRoot(
    noteRepository: NoteRepository,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    lockPrefs: LockPrefs,
    updatePrefs: UpdatePrefs,
    rememberUpdateState: RememberUpdateState,
    appReviewLauncher: AppReviewLauncher,
    playInAppUpdateProgressController: PlayInAppUpdateProgressController,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    appUnlocked: MutableStateFlow<Boolean>,
) {
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(
        initialValue = OnboardingState(),
    )
    val updatePreferencesState by updatePrefs.state.collectAsStateWithLifecycle(
        initialValue = UpdatePreferencesState(),
    )
    val unlocked by appUnlocked.collectAsStateWithLifecycle(initialValue = false)
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val updateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val realPlayBannerState by playInAppUpdateProgressController.bannerUiState.collectAsStateWithLifecycle()
    val devReleaseMockPlayBannerState by rememberUpdateState.devReleasePlayBannerMockUiState.collectAsStateWithLifecycle()
    val playBannerState =
        if (devReleaseMockPlayBannerState != PlayInAppUpdateBannerUiState.Hidden) {
            devReleaseMockPlayBannerState
        } else {
            realPlayBannerState
        }
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    var openSettingsRequest by rememberSaveable { mutableIntStateOf(0) }
    var openUpdateSheetRequest by rememberSaveable { mutableIntStateOf(0) }
    var startPlayInAppUpdateRequest by rememberSaveable { mutableIntStateOf(0) }
    var dismissedUpdateBarKey by rememberSaveable { mutableStateOf<String?>(null) }
    val currentLockState = lockState

    if (currentLockState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    if (currentLockState.enabled && !unlocked) {
        LockScreen(
            biometricEnabled = currentLockState.biometric,
            hasPin = currentLockState.hasPin,
            pinLength = currentLockState.pinLength,
            onUnlocked = { appUnlocked.value = true },
            verify = { pin -> lockPrefs.verify(pin) },
        )
    } else {
        activity?.let { fragmentActivity ->
            InAppRatingAutoPromptHost(
                onboardingState = onboardingState,
                updateState = updatePreferencesState,
                activity = fragmentActivity,
                updatePrefs = updatePrefs,
                appReviewLauncher = appReviewLauncher,
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(
            dev.bikram.remember.ui.theme.LocalSnackbarHostState provides snackbarHostState,
        ) {
            val currentUpdateInfo = updateInfo
            val updateKey = currentUpdateInfo?.notificationDedupeKey()
            val updateAvailable = BuildConfig.SHOW_UPDATES && currentUpdateInfo != null
            val updateFabState =
                when (val currentPlayState = playBannerState) {
                    is PlayInAppUpdateBannerUiState.Downloading ->
                        UpdateChromeState.Downloading(
                            bytesDownloaded = currentPlayState.bytesDownloaded,
                            totalBytesToDownload = currentPlayState.totalBytesToDownload,
                            indeterminateProgress = currentPlayState.indeterminateProgress,
                        )
                    PlayInAppUpdateBannerUiState.ReadyToInstall -> UpdateChromeState.ReadyToInstall
                    PlayInAppUpdateBannerUiState.Hidden ->
                        if (updateAvailable) {
                            UpdateChromeState.Available
                        } else {
                            UpdateChromeState.Hidden
                        }
                }
            val updateBarState =
                when (val currentPlayState = playBannerState) {
                    is PlayInAppUpdateBannerUiState.Downloading ->
                        UpdateChromeState.Downloading(
                            bytesDownloaded = currentPlayState.bytesDownloaded,
                            totalBytesToDownload = currentPlayState.totalBytesToDownload,
                            indeterminateProgress = currentPlayState.indeterminateProgress,
                        )
                    PlayInAppUpdateBannerUiState.ReadyToInstall -> UpdateChromeState.ReadyToInstall
                    PlayInAppUpdateBannerUiState.Hidden ->
                        if (updateAvailable && updateKey != dismissedUpdateBarKey) {
                            UpdateChromeState.Available
                        } else {
                            UpdateChromeState.Hidden
                        }
                }
            RememberNavGraph(
                repository = noteRepository,
                onboardingPrefs = onboardingPrefs,
                interactionPrefs = interactionPrefs,
                appScope = appScope,
                launchFlow = launchFlow,
                openSettingsRequest = openSettingsRequest,
                openUpdateSheetRequest = openUpdateSheetRequest,
                startPlayInAppUpdateRequest = startPlayInAppUpdateRequest,
                updateBarState = updateBarState,
                updateFabState = updateFabState,
                onUpdateClick = {
                    openSettingsRequest += 1
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
                        startPlayInAppUpdateRequest += 1
                    } else {
                        openUpdateSheetRequest += 1
                    }
                },
                onDismissUpdateAvailable = { dismissedUpdateBarKey = updateKey },
                onInstallUpdate = {
                    if (!rememberUpdateState.devReleaseCompletePlayUpdateIfReady()) {
                        val activity = context as? Activity
                        if (activity != null) {
                            playInAppUpdateProgressController.completeFlexibleUpdateIfReady(activity)
                        }
                    }
                },
            )
        }
    }
}
