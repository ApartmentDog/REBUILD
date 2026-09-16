package com.evyr.rebuild.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint / face unlock on top of the PIN.
 *
 * The PIN stays the source of truth and the fallback: biometrics are offered only
 * when the device actually has something enrolled, and a failed or cancelled
 * prompt just drops back to the keypad rather than locking anyone out. Nothing
 * here replaces or weakens the stored PIN hash.
 */
object BiometricUnlock {

    /** True when the device has hardware and at least one enrolled biometric. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (!isAvailable(activity)) {
            onFailure("No biometrics enrolled on this device")
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelling or backing out isn't an error worth surfacing --
                    // they've just chosen to type the PIN instead.
                    val userDismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!userDismissed) onFailure(errString.toString())
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("REBUILD")
                .setSubtitle("Unlock with fingerprint")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
}
