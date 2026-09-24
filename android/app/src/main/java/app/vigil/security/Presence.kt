package app.vigil.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface PresenceResult {
    data object Confirmed : PresenceResult
    data object Cancelled : PresenceResult
    data class Failed(val message: String) : PresenceResult
}

/** Asks the person holding the phone to prove they are its owner: fingerprint, face or screen lock. */
object Presence {
    suspend fun confirm(activity: FragmentActivity, title: String, subtitle: String): PresenceResult =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(PresenceResult.Confirmed)
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        if (!cont.isActive) return
                        val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                            code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            code == BiometricPrompt.ERROR_CANCELED
                        cont.resume(if (cancelled) PresenceResult.Cancelled else PresenceResult.Failed(message.toString()))
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
