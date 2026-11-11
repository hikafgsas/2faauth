package com.example.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class SensitiveClipboard(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun copyCode(code: String) {
        val token = "Authenticator-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(token, code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        manager.setPrimaryClip(clip)
        scope.launch {
            delay(30_000)
            // Only clear the app's own clip; preserve anything copied afterwards.
            runCatching {
                if (manager.primaryClipDescription?.label?.toString() == token) {
                    if (Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                    else manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
