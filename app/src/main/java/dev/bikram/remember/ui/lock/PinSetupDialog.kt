package dev.bikram.remember.ui.lock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.components.RememberTextButton

@Composable
fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val mismatch = pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm
    val ready = pin.length in 4..6 && pin == confirm

    AppBottomSheet(
        title = stringResource(R.string.pin_setup_title),
        subtitle = stringResource(R.string.pin_setup_subtitle),
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            RememberTextButton(enabled = ready, onClick = { onConfirm(pin) }) { Text(stringResource(R.string.common_save)) }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = { Text(stringResource(R.string.pin_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it },
                label = { Text(stringResource(R.string.pin_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = mismatch,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mismatch) {
                Text(
                    "PINs don't match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
