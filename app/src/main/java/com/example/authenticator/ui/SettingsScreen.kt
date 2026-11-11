package com.example.authenticator.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.authenticator.data.AppSettings
import com.example.authenticator.data.ThemeMode

@Composable
fun SettingsScreen(settings: AppSettings, onTheme: (ThemeMode) -> Unit,
    onSecureScreen: (Boolean) -> Unit, onAppLock: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Оформление", style = MaterialTheme.typography.titleMedium)
        SettingsCard {
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().clickable { onTheme(mode) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = settings.theme == mode, onClick = { onTheme(mode) })
                    Text(mode.title, Modifier.padding(start = 8.dp))
                }
            }
        }
        Text("Безопасность", style = MaterialTheme.typography.titleMedium)
        SettingsCard {
            SettingSwitch("Блокировка приложения",
                if (Build.VERSION.SDK_INT >= 30) "Биометрия или PIN телефона при каждом возвращении в приложение."
                else "Надёжная биометрия при каждом возвращении в приложение. На Android 8–10 PIN не поддерживается.",
                settings.appLock, { onAppLock() })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingSwitch("Защита экрана", "Запрещает скриншоты, запись экрана и снимок в обзоре открытых приложений.",
                settings.secureScreen, onSecureScreen)
        }
        Text("О приложении", style = MaterialTheme.typography.titleMedium)
        SettingsCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Authenticator · 1.0", style = MaterialTheme.typography.titleMedium)
                Text("TOTP по RFC 6238. Аккаунты хранятся только на устройстве; для кодов и QR-сканера интернет не нужен.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Данные зашифрованы AES-256-GCM. Ключ шифрования находится в Android Keystore.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Если код не подходит, включите автоматическую дату и время на телефоне.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Экспорта и синхронизации в этой версии нет. Храните резервные коды сервисов отдельно.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
