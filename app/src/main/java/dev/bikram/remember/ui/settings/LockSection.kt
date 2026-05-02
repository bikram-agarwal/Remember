package dev.bikram.remember.ui.settings

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Security section: app-lock toggle (uses the device's keyguard / PIN to unlock the
 * app on launch) and the biometric-unlock toggle (only enabled if the device has
 * biometric hardware AND the app lock itself is on). Pulled out of [SettingsRoute] in
 * audit 3.1.
 */
@Composable
internal fun LockSection(
    lockState: LockPrefs.State,
    lockPrefs: LockPrefs,
    biometricAvailable: Boolean,
    deviceCredentialAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val resources = LocalResources.current
    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            ToggleRow(
                materialSymbolName = "lock",
                title = stringResource(R.string.settings_app_lock_title),
                subtitle =
                    when {
                        !deviceCredentialAvailable -> stringResource(R.string.settings_app_lock_no_device_lock)
                        lockState.enabled -> stringResource(R.string.settings_app_lock_enabled)
                        else -> stringResource(R.string.settings_app_lock_disabled)
                    },
                checked = lockState.enabled,
                enabled = deviceCredentialAvailable || lockState.enabled,
                onChange = { want ->
                    if (want) {
                        scope.launch {
                            if (deviceCredentialAvailable) {
                                lockPrefs.enableDeviceCredential()
                            } else {
                                snackbarHostState.showSnackbar(
                                    message = resources.getString(R.string.settings_app_lock_no_device_lock),
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    } else {
                        scope.launch { lockPrefs.disable() }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            ToggleRow(
                materialSymbolName = "fingerprint",
                title = stringResource(R.string.settings_biometric_title),
                subtitle =
                    when {
                        !biometricAvailable -> stringResource(R.string.settings_biometric_no_hardware)
                        !lockState.enabled -> stringResource(R.string.settings_biometric_need_lock)
                        lockState.biometric -> stringResource(R.string.settings_biometric_enabled)
                        else -> stringResource(R.string.settings_biometric_disabled)
                    },
                checked = lockState.biometric,
                enabled = biometricAvailable && lockState.enabled,
                onChange = { scope.launch { lockPrefs.setBiometric(it) } },
            )
        }
    }
}
