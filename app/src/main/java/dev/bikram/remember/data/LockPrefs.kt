package dev.bikram.remember.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        val saltAndHash = withContext(Dispatchers.Default) {
            val salt = randomSalt()
            salt to hashPin(pin, salt)
        }
        val (salt, hash) = saltAndHash
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
        return withContext(Dispatchers.Default) {
            val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            val candidate = hashPin(pin, salt)
            constantTimeEquals(Base64.decode(storedHash, Base64.NO_WRAP), candidate)
        }
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
        // Restore from a backup is conservative for security-sensitive keys: we only ever
        // overwrite PIN_HASH / PIN_SALT / PIN_LENGTH when the backup actually carries a
        // usable replacement. Backups taken before the user set a PIN will either lack
        // these keys entirely or carry empty placeholder strings (see exportForBackup),
        // and either case must NOT wipe an existing PIN - that would silently disable
        // app lock without user consent.
        context.lockDataStore.edit { mutable ->
            if (json.has(Keys.ENABLED.name) && !json.isNull(Keys.ENABLED.name)) {
                mutable[Keys.ENABLED] = json.getBoolean(Keys.ENABLED.name)
            }
            if (json.has(Keys.BIOMETRIC.name) && !json.isNull(Keys.BIOMETRIC.name)) {
                mutable[Keys.BIOMETRIC] = json.getBoolean(Keys.BIOMETRIC.name)
            }
            val backupPinHash = json.optString(Keys.PIN_HASH.name, "").takeIf { it.isNotBlank() }
            if (backupPinHash != null) {
                mutable[Keys.PIN_HASH] = backupPinHash
            }
            val backupPinSalt = json.optString(Keys.PIN_SALT.name, "").takeIf { it.isNotBlank() }
            if (backupPinSalt != null) {
                mutable[Keys.PIN_SALT] = backupPinSalt
            }
            if (json.has(Keys.PIN_LENGTH.name) && !json.isNull(Keys.PIN_LENGTH.name)) {
                val backupPinLength = json.optInt(Keys.PIN_LENGTH.name, -1)
                if (backupPinLength in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
                    mutable[Keys.PIN_LENGTH] = backupPinLength
                }
            }
        }
    }
}
