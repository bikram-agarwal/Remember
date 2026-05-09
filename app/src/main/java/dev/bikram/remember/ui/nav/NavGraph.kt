package dev.bikram.remember.ui.nav

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import dev.bikram.remember.ui.edit.EditListRoute
import dev.bikram.remember.ui.edit.EditNoteRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.bikram.remember.ui.help.HelpScreen
import dev.bikram.remember.ui.help.HelpViewModel
import dev.bikram.remember.ui.main.MainTabScaffold
import dev.bikram.remember.ui.onboarding.OnboardingPermissionsScreen
import dev.bikram.remember.ui.onboarding.OnboardingTitleScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING_TITLE = "onboardingTitle"
    const val ONBOARDING_PERMISSIONS = "onboardingPermissions"
    const val EXTERNAL_LAUNCH = "externalLaunch"
    const val MAIN = "main"
    const val EDIT_NOTE = "editNote"
    const val EDIT_LIST = "editList"
    const val GOOGLE_TASKS_IMPORT = "googleTasksImport"
    const val HELP = "help"
    const val ARG_ID = "id"
    const val ARG_PREFILL = "prefill"
    const val ARG_FORCE_EDIT = "forceEdit"
    const val ARG_EXIT_ON_BACK = "exitOnBack"

    fun editNote(
        id: Long?,
        prefill: String = "",
        forceEdit: Boolean = false,
        exitOnBack: Boolean = false,
    ): String {
        val p = if (prefill.isNotEmpty()) "&$ARG_PREFILL=${Uri.encode(prefill)}" else ""
        val f = if (forceEdit) "&$ARG_FORCE_EDIT=true" else ""
        val e = if (exitOnBack) "&$ARG_EXIT_ON_BACK=true" else ""
        return "$EDIT_NOTE?${ARG_ID}=${id ?: -1L}$p$f$e"
    }

    fun editList(
        id: Long?,
        forceEdit: Boolean = false,
        exitOnBack: Boolean = false,
    ): String {
        val f = if (forceEdit) "&${ARG_FORCE_EDIT}=true" else ""
        val e = if (exitOnBack) "&$ARG_EXIT_ON_BACK=true" else ""
        return "$EDIT_LIST?${ARG_ID}=${id ?: -1L}$f$e"
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
    openUpdateSheetRequest: Int = 0,
) {
    val navController = rememberNavController()
    val helpVm: HelpViewModel = hiltViewModel()
    var settingsHighlightSection by remember { mutableStateOf<String?>(null) }
    var openSettingsUpdatesRequest by remember { mutableIntStateOf(0) }
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val currentOnboardingState = onboardingState
    val onboardingScope = rememberCoroutineScope()

    if (currentOnboardingState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
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
                        navController.navigate(Routes.MAIN) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                        openSettingsUpdatesRequest += 1
                    }
                }
            } finally {
                launchFlow.value = null
            }
        }
    }

    androidx.compose.animation.SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = lockedStartDestination,
        ) {
            composable(
                route = Routes.ONBOARDING_TITLE,
                enterTransition = {
                    if (initialState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                        slideInHorizontally { -it } + fadeIn()
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                        slideOutHorizontally { -it / 3 } + fadeOut()
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
                        slideInHorizontally { it } + fadeIn()
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_TITLE ||
                        targetState.destination.route == Routes.MAIN
                    ) {
                        slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        null
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                        slideInHorizontally { it } + fadeIn()
                    } else {
                        null
                    }
                },
                popExitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_TITLE) {
                        slideOutHorizontally { it } + fadeOut()
                    } else {
                        null
                    }
                },
            ) {
                OnboardingPermissionsScreen(
                    onContinue = {
                        onboardingScope.launch {
                            onboardingPrefs.markIntroSeen()
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
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable,
                ) {
                    MainTabScaffold(
                        repository = repository,
                        interactionPrefs = interactionPrefs,
                        onCreateNote = { navController.navigate(Routes.editNote(null)) },
                        onCreateList = { navController.navigate(Routes.editList(null)) },
                        onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                        onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                        onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                        onOpenHelp = { navController.navigate(Routes.HELP) },
                        settingsHighlightSection = settingsHighlightSection,
                        onSettingsHighlightHandled = { settingsHighlightSection = null },
                        openSettingsRequest = openSettingsRequest + openSettingsUpdatesRequest,
                        openUpdateSheetRequest = openUpdateSheetRequest + openSettingsUpdatesRequest,
                    )
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
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { it } + fadeOut() },
                popEnterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) {
                HelpScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAppSection = { sectionKey ->
                        settingsHighlightSection = sectionKey
                        navController.popBackStack()
                    },
                    helpVm = helpVm,
                )
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
