package dev.bikram.remember.ui.nav

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.googletasks.GoogleTasksImportRoute
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.common.rememberShareAppAction
import dev.bikram.remember.ui.components.UpdateChromeState
import dev.bikram.remember.ui.components.alertChromeSummary
import dev.bikram.remember.ui.edit.EditListRoute
import dev.bikram.remember.ui.edit.EditNoteRoute
import dev.bikram.remember.ui.help.HelpScreen
import dev.bikram.remember.ui.help.HelpViewModel
import dev.bikram.remember.ui.history.HistoryRoute
import dev.bikram.remember.ui.history.HistorySection
import dev.bikram.remember.ui.home.HomeRoute
import dev.bikram.remember.ui.main.MainTab
import dev.bikram.remember.ui.main.MainTabScaffold
import dev.bikram.remember.ui.onboarding.OnboardingPermissionsScreen
import dev.bikram.remember.ui.onboarding.OnboardingTitleScreen
import dev.bikram.remember.ui.settings.DevOptionsRoute
import dev.bikram.remember.ui.settings.RememberUpdateViewModel
import dev.bikram.remember.ui.settings.SettingsRoute
import dev.bikram.remember.ui.theme.LocalReducedMotion
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING_TITLE = "onboardingTitle"
    const val ONBOARDING_PERMISSIONS = "onboardingPermissions"
    const val EXTERNAL_LAUNCH = "externalLaunch"
    const val MAIN = "main"
    const val NOTES = "notes"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT_CONTENT = "editContent"
    const val EDIT_NOTE = "editNote"
    const val EDIT_LIST = "editList"
    const val GOOGLE_TASKS_IMPORT = "googleTasksImport"
    const val HELP = "help"
    const val DEV_OPTIONS = "devOptions"
    const val ARG_ID = "id"
    const val ARG_TYPE = "type"
    const val ARG_PREFILL = "prefill"
    const val ARG_FORCE_EDIT = "forceEdit"
    const val ARG_EXIT_ON_BACK = "exitOnBack"
    const val TYPE_NOTE = "note"
    const val TYPE_LIST = "checklist"

    fun editContent(
        id: Long?,
        type: NoteKind? = null,
        prefill: String = "",
        forceEdit: Boolean = false,
        exitOnBack: Boolean = false,
    ): String {
        val t =
            type?.let { kind ->
                "&$ARG_TYPE=" +
                    when (kind) {
                        NoteKind.NOTE -> TYPE_NOTE
                        NoteKind.LIST -> TYPE_LIST
                    }
            } ?: ""
        val p = if (prefill.isNotEmpty()) "&$ARG_PREFILL=${Uri.encode(prefill)}" else ""
        val f = if (forceEdit) "&$ARG_FORCE_EDIT=true" else ""
        val e = if (exitOnBack) "&$ARG_EXIT_ON_BACK=true" else ""
        return "$EDIT_CONTENT?${ARG_ID}=${id ?: -1L}$t$p$f$e"
    }

    fun editNote(
        id: Long?,
        prefill: String = "",
        forceEdit: Boolean = false,
        exitOnBack: Boolean = false,
    ): String =
        editContent(
            id = id,
            type = NoteKind.NOTE,
            prefill = prefill,
            forceEdit = forceEdit,
            exitOnBack = exitOnBack,
        )

    fun editList(
        id: Long?,
        forceEdit: Boolean = false,
        exitOnBack: Boolean = false,
    ): String =
        editContent(
            id = id,
            type = NoteKind.LIST,
            forceEdit = forceEdit,
            exitOnBack = exitOnBack,
        )

    fun noteKindFor(type: String?): NoteKind? =
        when (type) {
            TYPE_NOTE -> NoteKind.NOTE
            TYPE_LIST -> NoteKind.LIST
            else -> null
        }
}

@Composable
fun RememberNavGraph(
    repository: NoteRepository,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>? = null,
    openSettingsRequest: Int = 0,
    updateVm: RememberUpdateViewModel,
    onUpdateCheckStarted: () -> Unit = {},
    updateBarState: UpdateChromeState = UpdateChromeState.Hidden,
    updateSignalEpoch: Int = 0,
    onUpdateClick: () -> Unit = {},
    onDismissUpdateAvailable: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
) {
    val navController = rememberNavController()
    val helpVm: HelpViewModel = hiltViewModel()
    var settingsHighlightSection by remember { mutableStateOf<String?>(null) }
    var openSettingsUpdatesRequest by remember { mutableIntStateOf(0) }
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val currentOnboardingState = onboardingState
    val onboardingScope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val navSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val navFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val navFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val devOptionsFadeSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    var historySection by rememberSaveable { mutableStateOf(HistorySection.ARCHIVE) }
    var historyVisibleItemCount by rememberSaveable { mutableIntStateOf(0) }
    var currentMainTab by rememberSaveable { mutableStateOf(MainTab.Notes) }
    var alertBarsExpanded by rememberSaveable { mutableStateOf(false) }
    var lastPresentedAlertKey by rememberSaveable { mutableStateOf<String?>(null) }
    var createNoteInPane by remember { mutableStateOf<(() -> Unit)?>(null) }
    var createListInPane by remember { mutableStateOf<(() -> Unit)?>(null) }
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val paneScaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    val useDualPaneMode = paneScaffoldDirective.maxHorizontalPartitions > 1

    if (currentOnboardingState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val notificationsAllowed = rememberNotificationsAllowed()
    val blockedReminderCountFlow =
        remember(repository, notificationsAllowed) {
            if (notificationsAllowed) {
                flowOf(0)
            } else {
                repository
                    .observeActive()
                    .map { activeNotes ->
                        val alertWindowEnd = System.currentTimeMillis() + REMINDER_NOTIFICATION_ALERT_WINDOW_MS
                        activeNotes.count { noteWithItems ->
                            val reminderAt = noteWithItems.note.reminderAt
                            reminderAt != null &&
                                reminderAt <= alertWindowEnd &&
                                noteWithItems.note.completedAt == null
                        }
                    }.distinctUntilChanged()
            }
        }
    val blockedReminderCount =
        blockedReminderCountFlow.collectAsStateWithLifecycle(initialValue = 0).value
    val alertSummary =
        remember(updateBarState, blockedReminderCount) {
            alertChromeSummary(updateBarState, blockedReminderCount)
        }
    val alertPresentationKey =
        remember(updateBarState, alertSummary.count, blockedReminderCount > 0) {
            if (alertSummary.count == 0) {
                null
            } else {
                "${updateStatePresentationKey(updateBarState)}:notifications-disabled-${blockedReminderCount > 0}"
            }
        }
    // Also keyed on lastPresentedAlertKey: re-running the update check clears it below,
    // which must re-present the alert even when the alert state itself never changed
    // (e.g. an update that is still "available" after a manual re-check).
    LaunchedEffect(alertPresentationKey, lastPresentedAlertKey) {
        val currentAlertKey = alertPresentationKey
        if (currentAlertKey == null) {
            alertBarsExpanded = false
            lastPresentedAlertKey = null
        } else if (currentAlertKey != lastPresentedAlertKey) {
            alertBarsExpanded = true
            lastPresentedAlertKey = currentAlertKey
        }
    }
    val handleUpdateCheckStarted = {
        // Forget the presented alert so the chrome pops again for the re-checked result.
        lastPresentedAlertKey = null
        onUpdateCheckStarted()
    }
    // Any (re-)delivery of an update — manual check, background worker, dev mock — bumps
    // the epoch and re-presents the alert. The handled epoch is saveable so rotation
    // does not count as a new delivery.
    var lastHandledUpdateSignalEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(updateSignalEpoch) {
        if (updateSignalEpoch != lastHandledUpdateSignalEpoch) {
            lastHandledUpdateSignalEpoch = updateSignalEpoch
            lastPresentedAlertKey = null
        }
    }

    val initialExternalLaunch =
        remember(launchFlow, currentOnboardingState.hasSeenIntro) {
            currentOnboardingState.hasSeenIntro &&
                launchFlow?.value?.let { action ->
                    action is LaunchAction.OpenNote && action.exitOnBack
                } == true
        }
    val lockedStartDestination =
        remember(currentOnboardingState.hasSeenIntro, initialExternalLaunch) {
            when {
                !currentOnboardingState.hasSeenIntro -> Routes.ONBOARDING_TITLE
                initialExternalLaunch -> Routes.EXTERNAL_LAUNCH
                else -> Routes.MAIN
            }
        }

    val openMainTab: (MainTab) -> Unit = { selectedTab ->
        currentMainTab = selectedTab
        navController.navigate(Routes.MAIN) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(launchFlow, currentOnboardingState.hasSeenIntro) {
        if (!currentOnboardingState.hasSeenIntro) return@LaunchedEffect
        launchFlow?.collectLatest { action ->
            if (action == null) return@collectLatest
            try {
                when (action) {
                    is LaunchAction.NewNote -> navController.navigate(Routes.editNote(null, action.prefill))
                    LaunchAction.NewList -> navController.navigate(Routes.editList(null))
                    is LaunchAction.OpenNote -> {
                        val note = repository.get(action.id)
                        if (note == null) {
                            if (action.exitOnBack) {
                                currentMainTab = MainTab.Notes
                                navController.navigate(Routes.MAIN) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                            return@collectLatest
                        }
                        navController.openEditRouteFor(note, exitOnBack = action.exitOnBack)
                    }
                    LaunchAction.OpenSettingsUpdates -> {
                        currentMainTab = MainTab.Settings
                        navController.navigate(Routes.MAIN) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                        openSettingsUpdatesRequest += 1
                        updateVm.requestOpenSheet()
                    }
                }
            } finally {
                launchFlow.value = null
            }
        }
    }

    val settingsRequest = openSettingsRequest + openSettingsUpdatesRequest
    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0) {
            openMainTab(MainTab.Settings)
        }
    }
    LaunchedEffect(settingsHighlightSection) {
        if (settingsHighlightSection != null) {
            openMainTab(MainTab.Settings)
        }
    }
    androidx.compose.animation.SharedTransitionLayout {
        val navHostContent: @Composable (Int) -> Unit = { closeNotesRevealRequest ->
            NavHost(
                navController = navController,
                startDestination = lockedStartDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(
                    route = Routes.ONBOARDING_TITLE,
                    enterTransition = {
                        if (initialState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                            if (reducedMotion) {
                                EnterTransition.None
                            } else {
                                slideInHorizontally(animationSpec = navSpatialSpec) { -it } +
                                    fadeIn(animationSpec = navFadeInSpec)
                            }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                            if (reducedMotion) {
                                ExitTransition.None
                            } else {
                                slideOutHorizontally(animationSpec = navSpatialSpec) { -it / 3 } +
                                    fadeOut(animationSpec = navFadeOutSpec)
                            }
                        } else {
                            null
                        }
                    },
                ) {
                    OnboardingTitleScreen(
                        onLetsBegin = {
                            navController.navigate(Routes.ONBOARDING_PERMISSIONS)
                        },
                    )
                }

                composable(
                    route = Routes.ONBOARDING_PERMISSIONS,
                    enterTransition = {
                        if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                EnterTransition.None
                            } else {
                                slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeIn(animationSpec = navFadeInSpec)
                            }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.route == Routes.ONBOARDING_TITLE ||
                            targetState.destination.route == Routes.MAIN
                        ) {
                            if (reducedMotion) {
                                ExitTransition.None
                            } else {
                                slideOutHorizontally(animationSpec = navSpatialSpec) { -it / 3 } +
                                    fadeOut(animationSpec = navFadeOutSpec)
                            }
                        } else {
                            null
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                EnterTransition.None
                            } else {
                                slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeIn(animationSpec = navFadeInSpec)
                            }
                        } else {
                            null
                        }
                    },
                    popExitTransition = {
                        if (targetState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                ExitTransition.None
                            } else {
                                slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeOut(animationSpec = navFadeOutSpec)
                            }
                        } else {
                            null
                        }
                    },
                ) {
                    OnboardingPermissionsScreen(
                        onContinue = {
                            onboardingScope.launch {
                                onboardingPrefs.markIntroSeen()
                                currentMainTab = MainTab.Notes
                                navController.navigate(Routes.MAIN) {
                                    popUpTo(navController.graph.id) {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                    )
                }

                composable(Routes.EXTERNAL_LAUNCH) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                composable(Routes.MAIN) {
                    val shareApp = rememberShareAppAction()
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        MainTabScaffold(
                            repository = repository,
                            currentTab = currentMainTab,
                            useDualPaneMode = useDualPaneMode,
                            onTabSelected = openMainTab,
                            onCreateNote = {
                                if (useDualPaneMode && createNoteInPane != null) {
                                    createNoteInPane?.invoke()
                                } else {
                                    navController.navigate(Routes.editNote(null))
                                }
                            },
                            onCreateList = {
                                if (useDualPaneMode && createListInPane != null) {
                                    createListInPane?.invoke()
                                } else {
                                    navController.navigate(Routes.editList(null))
                                }
                            },
                            onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                            historySection = historySection,
                            historyVisibleItemCount = historyVisibleItemCount,
                            updateBarState = updateBarState,
                            onUpdateClick = onUpdateClick,
                            onDismissUpdateAvailable = onDismissUpdateAvailable,
                            onInstallUpdate = onInstallUpdate,
                            alertSummary = alertSummary,
                            blockedReminderCount = blockedReminderCount,
                            alertBarsExpanded = alertBarsExpanded,
                            onAlertBarsExpandedChange = { expanded -> alertBarsExpanded = expanded },
                        ) { closeRevealRequest ->
                            AnimatedContent(
                                targetState = currentMainTab,
                                transitionSpec = {
                                    if (reducedMotion) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                        if (useDualPaneMode) {
                                            (
                                                slideInVertically(animationSpec = navSpatialSpec) { direction * it } +
                                                    fadeIn(animationSpec = navFadeInSpec)
                                            ) togetherWith (
                                                slideOutVertically(animationSpec = navSpatialSpec) { -direction * it / 3 } +
                                                    fadeOut(animationSpec = navFadeOutSpec)
                                            )
                                        } else {
                                            (
                                                slideInHorizontally(animationSpec = navSpatialSpec) { direction * it } +
                                                    fadeIn(animationSpec = navFadeInSpec)
                                            ) togetherWith (
                                                slideOutHorizontally(animationSpec = navSpatialSpec) { -direction * it / 3 } +
                                                    fadeOut(animationSpec = navFadeOutSpec)
                                            )
                                        }
                                    }.using(SizeTransform(clip = false))
                                },
                                label = "main_tab_content",
                            ) { tab ->
                                when (tab) {
                                    MainTab.Notes ->
                                        if (useDualPaneMode) {
                                            NotesTwoPaneRoute(
                                                interactionPrefs = interactionPrefs,
                                                appScope = appScope,
                                                closeRevealRequest = closeRevealRequest,
                                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                                onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                                                onOpenNoteInSinglePane = { note, forceEdit ->
                                                    navController.openEditRouteFor(note, forceEdit)
                                                },
                                                onCreateNoteInSinglePane = { navController.navigate(Routes.editNote(null)) },
                                                onCreateListInSinglePane = { navController.navigate(Routes.editList(null)) },
                                                onRegisterCreateNoteInPane = { callback -> createNoteInPane = callback },
                                                onRegisterCreateListInPane = { callback -> createListInPane = callback },
                                            )
                                        } else {
                                            HomeRoute(
                                                interactionPrefs = interactionPrefs,
                                                closeRevealRequest = closeRevealRequest,
                                                onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                                                onCreateNote = { navController.navigate(Routes.editNote(null)) },
                                                onCreateList = { navController.navigate(Routes.editList(null)) },
                                            )
                                        }

                                    MainTab.History ->
                                        if (useDualPaneMode) {
                                            HistoryTwoPaneRoute(
                                                interactionPrefs = interactionPrefs,
                                                appScope = appScope,
                                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                                section = historySection,
                                                onSectionChange = { selectedSection -> historySection = selectedSection },
                                                onVisibleItemCountChange = { visibleItemCount ->
                                                    historyVisibleItemCount = visibleItemCount
                                                },
                                                onOpenNoteInSinglePane = { note, forceEdit ->
                                                    navController.openEditRouteFor(note, forceEdit)
                                                },
                                            )
                                        } else {
                                            HistoryRoute(
                                                interactionPrefs = interactionPrefs,
                                                section = historySection,
                                                onSectionChange = { selectedSection -> historySection = selectedSection },
                                                onVisibleItemCountChange = { visibleItemCount ->
                                                    historyVisibleItemCount = visibleItemCount
                                                },
                                                onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                                            )
                                        }

                                    MainTab.Settings ->
                                        if (useDualPaneMode) {
                                            SettingsTwoPaneRoute(
                                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                                onOpenHelp = { navController.navigate(Routes.HELP) },
                                                onOpenDevOptions = { navController.navigate(Routes.DEV_OPTIONS) },
                                                updateVm = updateVm,
                                                onUpdateCheckStarted = handleUpdateCheckStarted,
                                                onShareApp = shareApp,
                                                highlightSectionKey = settingsHighlightSection,
                                                onHighlightHandled = { settingsHighlightSection = null },
                                            )
                                        } else {
                                            SettingsRoute(
                                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                                onOpenHelp = { navController.navigate(Routes.HELP) },
                                                onOpenDevOptions = { navController.navigate(Routes.DEV_OPTIONS) },
                                                updateVm = updateVm,
                                                onUpdateCheckStarted = handleUpdateCheckStarted,
                                                highlightSectionKey = settingsHighlightSection,
                                                onHighlightHandled = { settingsHighlightSection = null },
                                            )
                                        }
                                }
                            }
                        }
                    }
                }

                composable(
                    route = "${Routes.EDIT_CONTENT}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_TYPE}={${Routes.ARG_TYPE}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_EXIT_ON_BACK}={${Routes.ARG_EXIT_ON_BACK}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_TYPE) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_PREFILL) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_EXIT_ON_BACK) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                ) { entry ->
                    val context = LocalContext.current
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val requestedType = entry.arguments?.getString(Routes.ARG_TYPE).orEmpty()
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    val exitOnBack = entry.arguments?.getBoolean(Routes.ARG_EXIT_ON_BACK) ?: false
                    var resolvedKind by remember(id, requestedType) {
                        mutableStateOf(
                            Routes.noteKindFor(requestedType)
                                ?: if (id <= 0) NoteKind.NOTE else null,
                        )
                    }
                    LaunchedEffect(id, requestedType) {
                        if (resolvedKind == null && id > 0) {
                            resolvedKind = repository.get(id)?.note?.kind ?: NoteKind.NOTE
                        }
                    }
                    val onBack = {
                        if (exitOnBack) {
                            val activity = context as? Activity
                            if (activity != null) {
                                activity.finish()
                            } else {
                                navController.popBackStack()
                                Unit
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    val onNavigateUp = {
                        if (exitOnBack) {
                            currentMainTab = MainTab.Notes
                            navController.navigate(Routes.MAIN) {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        when (resolvedKind) {
                            NoteKind.LIST ->
                                EditListRoute(
                                    appScope = appScope,
                                    noteId = id.takeIf { it > 0 },
                                    forceEdit = forceEdit,
                                    onBack = onBack,
                                    onNavigateUp = onNavigateUp,
                                )
                            NoteKind.NOTE ->
                                EditNoteRoute(
                                    appScope = appScope,
                                    noteId = id.takeIf { it > 0 },
                                    forceEdit = forceEdit,
                                    onBack = onBack,
                                    onNavigateUp = onNavigateUp,
                                )
                            null -> Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                composable(
                    route = "${Routes.EDIT_NOTE}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_EXIT_ON_BACK}={${Routes.ARG_EXIT_ON_BACK}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_PREFILL) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_EXIT_ON_BACK) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                ) { entry ->
                    val context = LocalContext.current
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    val exitOnBack = entry.arguments?.getBoolean(Routes.ARG_EXIT_ON_BACK) ?: false
                    val onBack = {
                        if (exitOnBack) {
                            val activity = context as? Activity
                            if (activity != null) {
                                activity.finish()
                            } else {
                                navController.popBackStack()
                                Unit
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    val onNavigateUp = {
                        if (exitOnBack) {
                            currentMainTab = MainTab.Notes
                            navController.navigate(Routes.MAIN) {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        EditNoteRoute(
                            appScope = appScope,
                            noteId = id.takeIf { it > 0 },
                            forceEdit = forceEdit,
                            onBack = onBack,
                            onNavigateUp = onNavigateUp,
                        )
                    }
                }

                composable(Routes.GOOGLE_TASKS_IMPORT) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        GoogleTasksImportRoute(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    route = Routes.HELP,
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                ) {
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAppSection = { sectionKey ->
                            settingsHighlightSection = sectionKey
                            val returnedToExistingSettings =
                                navController.popBackStack(Routes.SETTINGS, inclusive = false)
                            if (!returnedToExistingSettings) {
                                openMainTab(MainTab.Settings)
                            }
                        },
                        helpVm = helpVm,
                    )
                }

                composable(
                    route = Routes.DEV_OPTIONS,
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = devOptionsFadeSpec)
                        }
                    },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        DevOptionsRoute(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    route = "${Routes.EDIT_LIST}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_EXIT_ON_BACK}={${Routes.ARG_EXIT_ON_BACK}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_EXIT_ON_BACK) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                ) { entry ->
                    val context = LocalContext.current
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    val exitOnBack = entry.arguments?.getBoolean(Routes.ARG_EXIT_ON_BACK) ?: false
                    val onBack = {
                        if (exitOnBack) {
                            val activity = context as? Activity
                            if (activity != null) {
                                activity.finish()
                            } else {
                                navController.popBackStack()
                                Unit
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    val onNavigateUp = {
                        if (exitOnBack) {
                            currentMainTab = MainTab.Notes
                            navController.navigate(Routes.MAIN) {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                            Unit
                        }
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        EditListRoute(
                            appScope = appScope,
                            noteId = id.takeIf { it > 0 },
                            forceEdit = forceEdit,
                            onBack = onBack,
                            onNavigateUp = onNavigateUp,
                        )
                    }
                }
            }
        }

        navHostContent(0)
    }
}

private fun NavController.openEditRouteFor(
    note: NoteWithItems,
    forceEdit: Boolean = false,
    exitOnBack: Boolean = false,
) {
    val route =
        when (note.note.kind) {
            NoteKind.NOTE -> Routes.editNote(note.note.id, forceEdit = forceEdit, exitOnBack = exitOnBack)
            NoteKind.LIST -> Routes.editList(note.note.id, forceEdit = forceEdit, exitOnBack = exitOnBack)
        }
    navigate(route) {
        if (exitOnBack) {
            popUpTo(graph.id) {
                inclusive = true
            }
        }
        launchSingleTop = true
    }
}

private const val REMINDER_NOTIFICATION_ALERT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

private fun updateStatePresentationKey(updateState: UpdateChromeState): String =
    when (updateState) {
        UpdateChromeState.Hidden -> "hidden"
        UpdateChromeState.Available -> "available"
        is UpdateChromeState.Downloading -> "downloading"
        UpdateChromeState.ReadyToInstall -> "ready"
    }
