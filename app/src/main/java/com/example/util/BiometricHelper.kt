package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricResult {
    object Success : BiometricResult
    data class Error(val errorCode: Int, val errString: CharSequence) : BiometricResult
    object Failed : BiometricResult
    object NegativeButton : BiometricResult
    data class Unavailable(val reason: String) : BiometricResult
}

object BiometricHelper {

    fun canUseSystemBiometric(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            result == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun getBiometricStatus(context: Context): String {
        return try {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> "AVAILABLE"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NOT_ENROLLED"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
                else -> "UNAVAILABLE"
            }
        } catch (_: Exception) {
            "UNAVAILABLE"
        }
    }

    fun authenticateWithFace(
        activity: FragmentActivity,
        title: String = "Face Unlock",
        subtitle: String = "Verify your face to continue",
        description: String = "Look at the front camera to scan and unlock",
        negativeButtonText: String = "Use PIN",
        onResult: (BiometricResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onResult(BiometricResult.NegativeButton)
                } else {
                    onResult(BiometricResult.Error(errorCode, errString))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(BiometricResult.Failed)
            }
        }

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onResult(BiometricResult.Unavailable(e.message ?: "Biometric prompt error"))
        }
    }
}
