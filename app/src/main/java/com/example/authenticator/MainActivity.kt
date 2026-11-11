package com.example.authenticator

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authenticator.core.OtpAccount
import com.example.authenticator.core.Totp
import com.example.authenticator.ui.AuthAction
import com.example.authenticator.ui.AuthenticatorRoot
import com.example.authenticator.ui.MainViewModel
import com.example.authenticator.ui.theme.AuthenticatorTheme
import com.example.authenticator.data.ThemeMode

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as AuthenticatorApplication).container)
    }
    private lateinit var prompt: BiometricPrompt
    private val authenticators: Int get() =
        if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.authenticated()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.cancelAuthentication()
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        viewModel.message("Не удалось подтвердить личность: $errString")
                    }
                }
            })
        receiveLink(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent)
                        else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(0xFF111119.toInt())
                        else SystemBarStyle.light(0xFFF7F7FC.toInt(), 0xFF111119.toInt())
                )
            }
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!settings.secureScreen)
            }
            AuthenticatorTheme(mode = settings.theme) {
                AuthenticatorRoot(viewModel, ::authenticate, ::copyCode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveLink(intent)
    }

    private fun receiveLink(source: Intent) {
        if (source.action == Intent.ACTION_VIEW && source.data?.scheme.equals("otpauth", true)) {
            viewModel.receiveUri(source.data.toString())
        }
        // Do not put secret-bearing links in saved state or retain them on the Activity intent.
        setIntent(Intent(source).setData(null))
    }

    override fun onStart() {
        super.onStart()
        viewModel.enteredForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) viewModel.lockWhenBackgrounded()
        super.onStop()
    }

    private fun authenticate(action: AuthAction) {
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.message(if (Build.VERSION.SDK_INT >= 30)
                "Настройте PIN-код, пароль или биометрию в настройках телефона."
            else "Для Android 8–10 настройте надёжную биометрию (например, отпечаток пальца).")
            return
        }
        viewModel.beginAuthentication(action)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticator")
            .setSubtitle(if (action == AuthAction.UNLOCK) "Откройте свои коды" else "Подтвердите изменение блокировки")
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Отмена")
        try { prompt.authenticate(info.build()) } catch (_: Exception) {
            viewModel.cancelAuthentication()
            viewModel.message("Системная аутентификация сейчас недоступна.")
        }
    }

    private fun copyCode(account: OtpAccount) {
        if (!viewModel.unlocked.value || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        try {
            val d = account.details
            val code = Totp.generate(d.secret, System.currentTimeMillis() / 1000, d.digits, d.period, d.algorithm)
            (application as AuthenticatorApplication).clipboard.copyCode(code)
            viewModel.message("Код скопирован. Буфер очистится примерно через 30 секунд.")
        } catch (_: Exception) { viewModel.message("Не удалось скопировать код.") }
    }
}
