package com.example.adobongkangkong

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.transfer.ImportRecipeBundleUseCase
import com.example.adobongkangkong.domain.transfer.ParseRecipeBundleUseCase
import com.example.adobongkangkong.ui.theme.AdobongKangkongTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var userPreferences: UserPreferencesRepository
    @Inject lateinit var parseRecipeBundleUseCase: ParseRecipeBundleUseCase
    @Inject lateinit var importRecipeBundleUseCase: ImportRecipeBundleUseCase

    private val appLockManager = AppLockManager()

    private var privacyLockEnabled by mutableStateOf(false)
    private var privacyLockTimeoutMinutes: Int? by mutableStateOf(null)
    private var isAuthenticatedForCurrentSession by mutableStateOf(true)

    private var biometricPromptShowing = false
    private var lastBackgroundTimeMs: Long = 0L
    private var appWasStartedWhenScreenTurnedOff = false
    private var skipNextUnlockPromptAfterDeviceUnlock = false

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!privacyLockEnabled) return

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    appWasStartedWhenScreenTurnedOff =
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

                    Log.d(
                        "AK_LOCK",
                        "Screen off → lock. appWasStarted=$appWasStartedWhenScreenTurnedOff"
                    )

                    appLockManager.lock()
                    isAuthenticatedForCurrentSession = false
                }

                Intent.ACTION_USER_PRESENT -> {
                    if (appWasStartedWhenScreenTurnedOff) {
                        Log.d("AK_LOCK", "User present after foreground screen lock → skip next app unlock")
                        skipNextUnlockPromptAfterDeviceUnlock = true
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerScreenLockReceiver()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferences.lockPortrait.collect { locked ->
                    requestedOrientation =
                        if (locked) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferences.privacyLockEnabled.collect { enabled ->
                    privacyLockEnabled = enabled

                    if (!enabled) {
                        appLockManager.unlock()
                        isAuthenticatedForCurrentSession = true
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferences.privacyLockTimeoutMinutes.collect { timeoutMinutes ->
                    privacyLockTimeoutMinutes = timeoutMinutes
                }
            }
        }

        handleIncomingIntent(intent)

        setContent {
            AdobongKangkongTheme {
                if (privacyLockEnabled && !isAuthenticatedForCurrentSession) {
                    Text("App locked")
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (!privacyLockEnabled) return

        lastBackgroundTimeMs = SystemClock.elapsedRealtime()

        if (privacyLockTimeoutMinutes == 0) {
            Log.d("AK_LOCK", "Immediate lock on background")
            appLockManager.lock()
            isAuthenticatedForCurrentSession = false
        }
    }

    override fun onResume() {
        super.onResume()

        if (!privacyLockEnabled) return

        if (skipNextUnlockPromptAfterDeviceUnlock && appLockManager.isLocked) {
            Log.d("AK_LOCK", "Skipping app unlock after device unlock")
            skipNextUnlockPromptAfterDeviceUnlock = false
            appWasStartedWhenScreenTurnedOff = false
            appLockManager.unlock()
            isAuthenticatedForCurrentSession = true
            return
        }

        val timeout = privacyLockTimeoutMinutes

        if (timeout != null && timeout > 0) {
            val elapsedMs = SystemClock.elapsedRealtime() - lastBackgroundTimeMs
            val timeoutMs = timeout * 60_000L

            if (elapsedMs >= timeoutMs) {
                Log.d("AK_LOCK", "Timeout reached → lock")
                appLockManager.lock()
                isAuthenticatedForCurrentSession = false
            }
        }

        if (appLockManager.isLocked) {
            isAuthenticatedForCurrentSession = false
            maybeShowPrivacyUnlockPrompt()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenLockReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            lifecycleScope.launch {
                val content = readTextFromUriSafe(data) ?: return@launch
                val parseResult = parseRecipeBundleUseCase.execute(content)

                if (parseResult is ParseRecipeBundleUseCase.Result.Success) {
                    importRecipeBundleUseCase.execute(parseResult.bundle)
                }
            }
        }
    }

    private suspend fun readTextFromUriSafe(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        buildString {
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                append(line).append("\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AK_IMPORT", "Error reading URI", e)
                null
            }
        }
    }

    private fun registerScreenLockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenLockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenLockReceiver, filter)
        }
    }

    private fun maybeShowPrivacyUnlockPrompt() {
        if (!privacyLockEnabled || !appLockManager.isLocked || biometricPromptShowing) return

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (
            BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return
        }

        biometricPromptShowing = true

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPromptShowing = false
                    appLockManager.unlock()
                    isAuthenticatedForCurrentSession = true
                }

                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    biometricPromptShowing = false
                    isAuthenticatedForCurrentSession = false
                }

                override fun onAuthenticationFailed() {
                    isAuthenticatedForCurrentSession = false
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock app")
            .setSubtitle("Verify to continue")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}

private class AppLockManager {
    var isLocked = false
        private set

    fun lock() {
        isLocked = true
    }

    fun unlock() {
        isLocked = false
    }
}