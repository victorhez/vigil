package app.vigil.security

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import app.vigil.solana.Keypair
import app.vigil.solana.PublicKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the device-bound pulse key: an Ed25519 key that can only check in, never move funds.
 *
 * The 32-byte seed is sealed with AES-256-GCM under a hardware-backed Android Keystore key.
 * On devices with a secure lock screen the Keystore key only unwraps within a short window
 * after biometric or device-credential authentication, so a check-in always needs the owner present.
 */
class PulseKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("pulse_key", Context.MODE_PRIVATE)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    val publicKey: PublicKey?
        get() = prefs.getString(PREF_PUBLIC, null)?.let(PublicKey::parseOrNull)

    /** Whether unwrapping the key needs a fresh biometric / device-credential check. */
    val requiresUserPresence: Boolean
        get() = prefs.getBoolean(PREF_AUTH, false)

    fun exists(): Boolean = publicKey != null && prefs.contains(PREF_SEALED)

    /** Creates a new pulse key, replacing any existing one. Returns its public key. */
    fun create(): PublicKey {
        val secure = keyguard?.isDeviceSecure == true
        val wrapKey = generateWrapKey(secure)
        val keypair = Keypair.generate()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
        val sealed = cipher.doFinal(keypair.seedBytes())

        prefs.edit {
            putString(PREF_PUBLIC, keypair.publicKey.toBase58())
            putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(PREF_SEALED, Base64.encodeToString(sealed, Base64.NO_WRAP))
            putBoolean(PREF_AUTH, secure)
        }
        return keypair.publicKey
    }

    /** Unwraps the pulse key. Must be called within the authentication window when [requiresUserPresence]. */
    fun unlock(): Keypair {
        val iv = Base64.decode(prefs.getString(PREF_IV, null) ?: error("No pulse key"), Base64.NO_WRAP)
        val sealed = Base64.decode(prefs.getString(PREF_SEALED, null) ?: error("No pulse key"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        val seed = cipher.doFinal(sealed)
        return Keypair.fromSeed(seed).also { seed.fill(0) }
    }

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrapKey(): SecretKey = keystore().getKey(ALIAS, null) as SecretKey

    private fun generateWrapKey(requireAuth: Boolean): SecretKey {
        keystore().deleteEntry(ALIAS)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (requireAuth) {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(
                        AUTH_WINDOW_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    // Enrolling a new fingerprint should not silently brick check-ins.
                    setInvalidatedByBiometricEnrollment(false)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "vigil.pulse.wrap"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AUTH_WINDOW_SECONDS = 30
        private const val PREF_PUBLIC = "public"
        private const val PREF_IV = "iv"
        private const val PREF_SEALED = "sealed"
        private const val PREF_AUTH = "auth"
    }
}
