package dev.bikram.remember.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.lockDataStore by preferencesDataStore(name = "lock_prefs")

class LockPrefs(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        /** Digits the user chose when enabling lock (4–6). Absent legacy installs default in [toState]. */
        val PIN_LENGTH = intPreferencesKey("pin_length")
    }

    data class State(
        val enabled: Boolean = false,
        val biometric: Boolean = false,
        val hasPin: Boolean = false,
        /** Length of the stored PIN for the unlock keypad (4–6). */
        val pinLength: Int = DEFAULT_PIN_LENGTH,
    )

    val state: Flow<State> = context.lockDataStore.data.map { p -> p.toState() }

    suspend fun enable(pin: String) {
        val salt = randomSalt()
        val hash = hashPin(pin, salt)
        val digitCount = pin.length.coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH)
        context.lockDataStore.edit {
            it[Keys.ENABLED] = true
            it[Keys.PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[Keys.PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[Keys.PIN_LENGTH] = digitCount
        }
    }

    suspend fun disable() {
        context.lockDataStore.edit {
            it[Keys.ENABLED] = false
            it[Keys.BIOMETRIC] = false
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_LENGTH)
        }
    }

    suspend fun setBiometric(enabled: Boolean) {
        context.lockDataStore.edit { it[Keys.BIOMETRIC] = enabled }
    }

    suspend fun verify(pin: String): Boolean {
        val p = context.lockDataStore.data.first()
        val storedHash = p[Keys.PIN_HASH] ?: return false
        val storedSalt = p[Keys.PIN_SALT] ?: return false
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val candidate = hashPin(pin, salt)
        return constantTimeEquals(Base64.decode(storedHash, Base64.NO_WRAP), candidate)
    }

    private fun Preferences.toState(): State {
        val storedLength = this[Keys.PIN_LENGTH]
        val pinLength = when {
            storedLength != null -> storedLength.coerceIn(MIN_PIN_LENGTH, MAX_PIN_LENGTH)
            this[Keys.PIN_HASH] != null -> DEFAULT_PIN_LENGTH
            else -> DEFAULT_PIN_LENGTH
        }
        return State(
            enabled = this[Keys.ENABLED] ?: false,
            biometric = this[Keys.BIOMETRIC] ?: false,
            hasPin = this[Keys.PIN_HASH] != null,
            pinLength = pinLength,
        )
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6
        private const val DEFAULT_PIN_LENGTH = 6
    }

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 50_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    suspend fun exportForBackup(): JSONObject {
        val prefs = context.lockDataStore.data.first()
        return JSONObject().apply {
            put(Keys.ENABLED.name, prefs[Keys.ENABLED] ?: false)
            put(Keys.BIOMETRIC.name, prefs[Keys.BIOMETRIC] ?: false)
            put(Keys.PIN_HASH.name, prefs[Keys.PIN_HASH].orEmpty())
            put(Keys.PIN_SALT.name, prefs[Keys.PIN_SALT].orEmpty())
            if (prefs[Keys.PIN_LENGTH] != null) {
                put(Keys.PIN_LENGTH.name, prefs[Keys.PIN_LENGTH]!!)
            }
        }
    }

    suspend fun importFromBackup(json: JSONObject?) {
        if (json == null || json.length() == 0) return
        context.lockDataStore.edit { mutable ->
            if (json.has(Keys.ENABLED.name) && !json.isNull(Keys.ENABLED.name)) {
                mutable[Keys.ENABLED] = json.getBoolean(Keys.ENABLED.name)
            }
            if (json.has(Keys.BIOMETRIC.name) && !json.isNull(Keys.BIOMETRIC.name)) {
                mutable[Keys.BIOMETRIC] = json.getBoolean(Keys.BIOMETRIC.name)
            }
            if (json.has(Keys.PIN_HASH.name) && !json.isNull(Keys.PIN_HASH.name)) {
                mutable[Keys.PIN_HASH] = json.getString(Keys.PIN_HASH.name)
            } else {
                mutable.remove(Keys.PIN_HASH)
            }
            if (json.has(Keys.PIN_SALT.name) && !json.isNull(Keys.PIN_SALT.name)) {
                mutable[Keys.PIN_SALT] = json.getString(Keys.PIN_SALT.name)
            } else {
                mutable.remove(Keys.PIN_SALT)
            }
            if (json.has(Keys.PIN_LENGTH.name) && !json.isNull(Keys.PIN_LENGTH.name)) {
                mutable[Keys.PIN_LENGTH] = json.getInt(Keys.PIN_LENGTH.name)
            } else {
                mutable.remove(Keys.PIN_LENGTH)
            }
        }
    }
}
