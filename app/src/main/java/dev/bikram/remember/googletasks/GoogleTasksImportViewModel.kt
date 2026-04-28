package dev.bikram.remember.googletasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Hosts the import flow's UI state machine.
 *
 * State transitions:
 *  - SignedOut -> tap "Connect" -> Identity Services authorize() runs.
 *      - If it returns Success silently (user has already consented in the past) we go straight
 *        to Loading -> Loaded. No UI is shown.
 *      - If it returns NeedsConsent we surface a [GoogleTasksImportEffect.LaunchConsent] which
 *        the screen forwards into a StartIntentSenderForResult launcher. The result returns to
 *        [onConsentResult] which calls authorize() again (or parses the result intent directly).
 *  - Loaded -> tap "Switch account" -> auth is forgotten, state resets, then the consent flow
 *    re-runs to surface the picker.
 *
 * The ViewModel never touches Activity context; effects are surfaced as one-shot events.
 */
@HiltViewModel
class GoogleTasksImportViewModel
    @Inject
    constructor(
        @param:ApplicationContext
        private val appContext: Context,
        private val repository: GoogleTasksRepository,
        private val importer: GoogleTasksImporter,
        private val prefs: GoogleTasksImportPrefs,
    ) : ViewModel() {
        private val auth: GoogleTasksAuthDelegate = DefaultGoogleTasksAuthDelegate
        private val takeoutParser = GoogleTasksTakeoutParser()

        private val _state = MutableStateFlow(GoogleTasksImportUiState())
        val state: StateFlow<GoogleTasksImportUiState> = _state.asStateFlow()

        private val _effects = MutableStateFlow<GoogleTasksImportEffect?>(null)
        val effects: StateFlow<GoogleTasksImportEffect?> = _effects.asStateFlow()

        private var cachedToken: String? = null

        init {
            viewModelScope.launch {
                prefs.lastAccountEmail.collect { email ->
                    _state.update { it.copy(rememberedEmail = email) }
                }
            }
        }

        fun consumeEffect() {
            _effects.value = null
        }

        fun setImportMethod(method: ImportMethod) {
            if (_state.value.selectedMethod == method) return
            cachedToken = null
            _state.update { current ->
                GoogleTasksImportUiState(
                    rememberedEmail = current.rememberedEmail,
                    selectedMethod = method,
                )
            }
        }

        /** User pressed "Connect Google account". Either silently fetches or launches the picker. */
        fun connect() {
            viewModelScope.launch {
                _state.update { it.copy(isFetching = true, error = null) }
                when (val auth = auth.authorize(appContext)) {
                    is GoogleTasksAuthorizationResult.Success -> handleAuthSuccess(auth)
                    is GoogleTasksAuthorizationResult.NeedsConsent -> {
                        _state.update { it.copy(isFetching = false) }
                        _effects.value = GoogleTasksImportEffect.LaunchConsent(auth.request)
                    }
                    is GoogleTasksAuthorizationResult.Failure -> {
                        _state.update {
                            it.copy(
                                isFetching = false,
                                error = ImportError.AuthFailed(auth.cause.localizedMessage),
                            )
                        }
                    }
                }
            }
        }

        fun loadTakeoutJson(uri: Uri) {
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        selectedMethod = ImportMethod.ManualImport,
                        isFetching = true,
                        error = null,
                        connected = false,
                        taskLists = emptyList(),
                        tasks = emptyList(),
                        selectedTaskIds = emptySet(),
                        takeoutStats = null,
                    )
                }
                val parsed =
                    runCatching {
                        val text =
                            withContext(Dispatchers.IO) {
                                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    inputStream.reader(Charsets.UTF_8).readText()
                                } ?: throw IllegalArgumentException("Could not open selected file")
                            }
                        takeoutParser.parse(text)
                    }
                parsed
                    .onSuccess { importData ->
                        val alreadyImported = prefs.importedMap()
                        _state.update {
                            it.copy(
                                selectedMethod = ImportMethod.ManualImport,
                                connected = true,
                                accountEmail = null,
                                isFetching = false,
                                taskLists = importData.taskLists,
                                tasks = importData.tasks,
                                takeoutStats = importData.stats,
                                alreadyImportedIds = alreadyImported.keys,
                                selectedTaskIds = emptySet(),
                                listFilterId = null,
                                error = null,
                            )
                        }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isFetching = false,
                                connected = false,
                                error = ImportError.TakeoutParseFailed(error.localizedMessage),
                            )
                        }
                    }
            }
        }

        /**
         * Activity result from a [GoogleTasksImportEffect.LaunchConsent] launch.
         *
         * Critically we trust whatever [GoogleTasksAuth.parseConsentResult] returns: if it gives a
         * Success we proceed; if it gives a NeedsConsent we launch the **specific** pendingIntent
         * it returned (e.g. the scope-consent step after the account picker resolved); if it gives
         * a Failure we surface an error. Falling back to a fresh [GoogleTasksAuth.authorize] here
         * would cause a loop because authorize() returns its own NeedsConsent (the picker), not
         * the next step of the in-flight consent flow.
         */
        fun onConsentResult(
            resultData: Intent?,
            approved: Boolean,
        ) {
            viewModelScope.launch {
                if (!approved) {
                    _state.update {
                        it.copy(isFetching = false, error = ImportError.ConsentDenied)
                    }
                    return@launch
                }
                // Hold the loading panel visible while we resolve token + email; otherwise the UI
                // briefly returns to the SignedOut state between the launcher callback and the
                // suspend calls below.
                _state.update { it.copy(isFetching = true, error = null) }
                when (val parsed = auth.parseConsentResult(appContext, resultData)) {
                    is GoogleTasksAuthorizationResult.Success -> handleAuthSuccess(parsed)
                    is GoogleTasksAuthorizationResult.NeedsConsent -> {
                        _state.update { it.copy(isFetching = false) }
                        _effects.value = GoogleTasksImportEffect.LaunchConsent(parsed.request)
                    }
                    is GoogleTasksAuthorizationResult.Failure ->
                        _state.update {
                            it.copy(
                                isFetching = false,
                                error = ImportError.AuthFailed(parsed.cause.localizedMessage),
                            )
                        }
                }
            }
        }

        private suspend fun handleAuthSuccess(auth: GoogleTasksAuthorizationResult.Success) {
            cachedToken = auth.accessToken
            if (auth.accountEmail.isNotBlank()) {
                prefs.setLastAccountEmail(auth.accountEmail)
            }
            _state.update {
                it.copy(
                    connected = true,
                    accountEmail = auth.accountEmail.ifBlank { it.accountEmail },
                    rememberedEmail = auth.accountEmail.ifBlank { it.rememberedEmail },
                    isFetching = true,
                    error = null,
                )
            }
            fetchEverything()
        }

        /**
         * "Switch account" - clear our app-side state, revoke the access token server-side if we
         * have one, and clear Identity Services' sign-in cache. After this call we run [connect]
         * which goes back through `authorize()`. Whether the picker actually appears depends on
         * Google's local credential cache: if it's been cleared, the picker shows; if Google
         * decides to silently reuse the last account, the user lands in that account. That's the
         * documented limitation of the Identity Services Authorization API and the in-app banner
         * tells the user to revoke at myaccount.google.com if they need a hard switch.
         */
        fun switchAccount() {
            viewModelScope.launch {
                _state.update { it.copy(isFetching = true, error = null) }
                auth.forget(appContext, cachedToken)
                prefs.reset()
                cachedToken = null
                _state.value = GoogleTasksImportUiState()
                connect()
            }
        }

        private suspend fun fetchEverything() {
            val token = cachedToken ?: return
            when (val listsResult = repository.fetchTaskLists(token)) {
                is GoogleTasksFetchResult.Success -> {
                    listsResult.refreshedAccessToken?.let { cachedToken = it }
                    if (listsResult.value.isEmpty()) {
                        _state.update { it.copy(isFetching = false, taskLists = emptyList(), tasks = emptyList()) }
                        return
                    }
                    fetchAllTasks(listsResult.value)
                }
                is GoogleTasksFetchResult.NeedsConsent -> {
                    _state.update { it.copy(isFetching = false) }
                    _effects.value = GoogleTasksImportEffect.LaunchConsent(listsResult.request)
                }
                is GoogleTasksFetchResult.AuthError ->
                    _state.update { it.copy(isFetching = false, error = ImportError.AuthFailed(listsResult.cause.localizedMessage)) }
                is GoogleTasksFetchResult.Network ->
                    _state.update { it.copy(isFetching = false, error = ImportError.Network) }
                is GoogleTasksFetchResult.Other ->
                    _state.update { it.copy(isFetching = false, error = ImportError.Unknown(listsResult.cause.localizedMessage)) }
            }
        }

        private suspend fun fetchAllTasks(lists: List<GoogleTaskList>) {
            val all = mutableListOf<TaskToImport>()
            var token = cachedToken ?: return
            lists.forEach { list ->
                when (val result = repository.fetchTasks(token, list.id)) {
                    is GoogleTasksFetchResult.Success -> {
                        result.refreshedAccessToken?.let {
                            token = it
                            cachedToken = it
                        }
                        result.value
                            .filter { it.deleted != true }
                            .forEach { task ->
                                all.add(TaskToImport(task = task, taskListId = list.id, taskListTitle = list.title))
                            }
                    }
                    is GoogleTasksFetchResult.NeedsConsent -> {
                        _state.update { it.copy(isFetching = false) }
                        _effects.value = GoogleTasksImportEffect.LaunchConsent(result.request)
                        return
                    }
                    is GoogleTasksFetchResult.AuthError -> {
                        _state.update { it.copy(isFetching = false, error = ImportError.AuthFailed(result.cause.localizedMessage)) }
                        return
                    }
                    is GoogleTasksFetchResult.Network -> {
                        _state.update { it.copy(isFetching = false, error = ImportError.Network) }
                        return
                    }
                    is GoogleTasksFetchResult.Other -> {
                        _state.update { it.copy(isFetching = false, error = ImportError.Unknown(result.cause.localizedMessage)) }
                        return
                    }
                }
            }
            val alreadyImported = prefs.importedMap()
            _state.update {
                it.copy(
                    isFetching = false,
                    taskLists = lists,
                    tasks = all,
                    takeoutStats = null,
                    alreadyImportedIds = alreadyImported.keys,
                    selectedTaskIds = it.selectedTaskIds.intersect(all.map { t -> t.task.id }.toSet()),
                    listFilterId = it.listFilterId.takeIf { id -> lists.any { l -> l.id == id } },
                    error = null,
                )
            }
        }

        fun toggleListFilter(listId: String?) {
            _state.update { it.copy(listFilterId = listId) }
        }

        fun toggleSelection(googleTaskId: String) {
            _state.update { current ->
                val next = current.selectedTaskIds.toMutableSet()
                if (!next.add(googleTaskId)) next.remove(googleTaskId)
                current.copy(selectedTaskIds = next)
            }
        }

        fun toggleSelectAll() {
            _state.update { current ->
                val visibleIds = current.visibleTasks().map { it.task.id }.toSet()
                val anyMissing = visibleIds.any { it !in current.selectedTaskIds }
                val next = current.selectedTaskIds.toMutableSet()
                if (anyMissing) next.addAll(visibleIds) else next.removeAll(visibleIds)
                current.copy(selectedTaskIds = next)
            }
        }

        fun setImportMode(mode: ImportMode) {
            _state.update { it.copy(importMode = mode) }
        }

        fun setOverwriteAlreadyImported(overwrite: Boolean) {
            _state.update { it.copy(overwriteAlreadyImported = overwrite) }
        }

        fun runImport() {
            val current = _state.value
            if (current.isImporting) return
            val toImport = current.visibleTasks().filter { it.task.id in current.selectedTaskIds }
            if (toImport.isEmpty()) return
            viewModelScope.launch {
                _state.update { it.copy(isImporting = true) }
                val alreadyImported = prefs.importedMap()
                val outcome =
                    importer.import(
                        tasks = toImport,
                        mode = current.importMode,
                        alreadyImported = alreadyImported,
                        overwrite = current.overwriteAlreadyImported,
                    )
                prefs.recordImported(outcome.googleTaskIdToRememberNoteId)
                _state.update {
                    it.copy(
                        isImporting = false,
                        selectedTaskIds = emptySet(),
                        alreadyImportedIds = it.alreadyImportedIds + outcome.googleTaskIdToRememberNoteId.keys,
                        lastOutcome = outcome,
                    )
                }
                _effects.value = GoogleTasksImportEffect.ImportFinished(outcome)
            }
        }
    }

data class GoogleTasksImportUiState(
    val rememberedEmail: String? = null,
    val selectedMethod: ImportMethod = ImportMethod.GrantPermission,
    val accountEmail: String? = null,
    /**
     * True once authorize() has returned a Success. Decoupled from [accountEmail] because the
     * Identity Services Authorization flow does not always populate the email - we still want
     * to show the loaded list even if the email fetch failed.
     */
    val connected: Boolean = false,
    val isFetching: Boolean = false,
    val isImporting: Boolean = false,
    val taskLists: List<GoogleTaskList> = emptyList(),
    val tasks: List<TaskToImport> = emptyList(),
    val takeoutStats: GoogleTasksTakeoutStats? = null,
    val selectedTaskIds: Set<String> = emptySet(),
    val alreadyImportedIds: Set<String> = emptySet(),
    val listFilterId: String? = null,
    val importMode: ImportMode = ImportMode.ONE_NOTE_PER_TASK,
    val overwriteAlreadyImported: Boolean = false,
    val error: ImportError? = null,
    val lastOutcome: ImportOutcome? = null,
) {
    fun visibleTasks(): List<TaskToImport> = if (listFilterId == null) tasks else tasks.filter { it.taskListId == listFilterId }

    val isLoaded: Boolean get() = !isFetching && connected && taskLists.isNotEmpty()
    val isEmpty: Boolean get() = !isFetching && connected && taskLists.isEmpty()
}

sealed class ImportError {
    data object Network : ImportError()

    data object ConsentDenied : ImportError()

    data class TakeoutParseFailed(
        val message: String?,
    ) : ImportError()

    data class AuthFailed(
        val message: String?,
    ) : ImportError()

    data class Unknown(
        val message: String?,
    ) : ImportError()
}

enum class ImportMethod {
    GrantPermission,
    ManualImport,
}

sealed class GoogleTasksImportEffect {
    /** The screen should launch [request] via StartIntentSenderForResult. */
    data class LaunchConsent(
        val request: IntentSenderRequest,
    ) : GoogleTasksImportEffect()

    data class ImportFinished(
        val outcome: ImportOutcome,
    ) : GoogleTasksImportEffect()
}
