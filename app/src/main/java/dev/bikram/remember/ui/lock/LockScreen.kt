package dev.bikram.remember.ui.lock

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import androidx.fragment.app.FragmentActivity
import dev.bikram.remember.data.LockPrefs
import kotlinx.coroutines.launch
import dev.bikram.remember.ui.feedback.tapSoundClickable

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    /** Number of digits required before verifying (4–6), from [LockPrefs.State.pinLength]. */
    pinLength: Int = LockPrefs.MAX_PIN_LENGTH,
    verify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val requiredDigits = pinLength.coerceIn(LockPrefs.MIN_PIN_LENGTH, LockPrefs.MAX_PIN_LENGTH)
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun tryUnlock() {
        scope.launch {
            if (verify(pin)) onUnlocked() else {
                error = true
                pin = ""
            }
        }
    }

    LaunchedEffect(biometricEnabled) {
        if (!biometricEnabled) return@LaunchedEffect
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        val mgr = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (mgr.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return@LaunchedEffect
        val prompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Remember")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RememberMaterialRoundedSymbol(
                    name = "lock",
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Unlock Remember",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (error) "Wrong PIN" else "Enter your PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                PinDots(length = pin.length, max = requiredDigits)
            }
            Keypad(
                onDigit = {
                    if (pin.length < requiredDigits) {
                        pin += it
                        error = false
                        if (pin.length == requiredDigits) tryUnlock()
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1); error = false
                    }
                },
                showBiometric = biometricEnabled,
                onBiometric = {
                    // Triggers LaunchedEffect only on first compose; re-run:
                    val activity = context as? FragmentActivity ?: return@Keypad
                    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
                    val prompt = BiometricPrompt(
                        activity,
                        activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                onUnlocked()
                            }
                        },
                    )
                    val info = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Remember")
                        .setNegativeButtonText("Use PIN")
                        .setAllowedAuthenticators(authenticators)
                        .build()
                    prompt.authenticate(info)
                },
            )
        }
    }
}

@Composable
private fun PinDots(length: Int, max: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(max) { idx ->
            val filled = idx < length
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    showBiometric: Boolean,
    onBiometric: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { d ->
                    DigitKey(d, onClick = { onDigit(d) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionKey(
                materialSymbolName = if (showBiometric) "fingerprint" else null,
                onClick = if (showBiometric) onBiometric else { {} },
            )
            DigitKey("0", onClick = { onDigit("0") })
            ActionKey(materialSymbolName = "backspace", onClick = onBackspace)
        }
    }
}

@Composable
private fun DigitKey(digit: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(72.dp)
            .tapSoundClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(digit, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ActionKey(materialSymbolName: String?, onClick: () -> Unit) {
    if (materialSymbolName == null) {
        Spacer(Modifier.size(72.dp))
        return
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(72.dp)
            .tapSoundClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            RememberMaterialRoundedSymbol(
                name = materialSymbolName,
                tint = MaterialTheme.colorScheme.onSurface,
                weight = FontWeight.Medium,
            )
        }
    }
}
