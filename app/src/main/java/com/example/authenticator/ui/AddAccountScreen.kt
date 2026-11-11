package com.example.authenticator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authenticator.core.OtpAlgorithm

@Composable
fun AddAccountScreen(vm: MainViewModel) {
    val state by vm.editor.collectAsStateWithLifecycle()
    val busy by vm.saving.collectAsStateWithLifecycle()
    val draft = state.draft
    var revealKey by remember { mutableStateOf(false) }
    var revealUri by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Подключите двухфакторную защиту", style = MaterialTheme.typography.titleLarge)
        Text("Откройте настройки 2FA нужного сервиса. Отсканируйте его QR-код или введите ключ вручную.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.navigate(Screen.SCAN) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner, null)
            Text("Сканировать QR-код", Modifier.padding(start = 8.dp))
        }
        SecretTextField(value = state.uri, onValueChange = { vm.changeEditor(state.copy(uri = it.take(8192))) },
            label = "Или вставьте otpauth:// ссылку", visible = revealUri, onVisibilityChange = { revealUri = !revealUri },
            enabled = !busy)
        TextButton(onClick = { vm.importUri(state.uri) }, enabled = state.uri.isNotBlank() && !busy) {
            Text("Заполнить поля из ссылки")
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        OutlinedTextField(value = draft.issuer, onValueChange = { vm.changeEditor(state.copy(draft = draft.copy(issuer = it.take(100)))) },
            label = { Text("Название сервиса") }, placeholder = { Text("Например, GitHub") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(value = draft.label, onValueChange = { vm.changeEditor(state.copy(draft = draft.copy(label = it.take(160)))) },
            label = { Text("Аккаунт или электронная почта") }, singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(), enabled = !busy)
        SecretTextField(value = draft.secret,
            onValueChange = { vm.changeEditor(state.copy(draft = draft.copy(secret = it.take(2048)))) },
            label = "Секретный ключ (Base32)", visible = revealKey, onVisibilityChange = { revealKey = !revealKey }, enabled = !busy)
        Text("Это ключ из настроек 2FA сервиса, а не пароль от аккаунта.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { advanced = !advanced }) {
            Text("Параметры TOTP")
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        // Imported non-default parameters are always visible for review.
        if (advanced || draft.algorithm != OtpAlgorithm.SHA1 || draft.digits != 6 || draft.period != 30) {
            Text("Алгоритм", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OtpAlgorithm.entries.forEach { algorithm ->
                    FilterChip(selected = draft.algorithm == algorithm, enabled = !busy,
                        onClick = { vm.changeEditor(state.copy(draft = draft.copy(algorithm = algorithm))) },
                        label = { Text(algorithm.name) })
                }
            }
            Text("Количество цифр", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (6..8).forEach { digits ->
                    FilterChip(selected = draft.digits == digits, enabled = !busy,
                        onClick = { vm.changeEditor(state.copy(draft = draft.copy(digits = digits))) },
                        label = { Text(digits.toString()) })
                }
            }
            OutlinedTextField(value = draft.period.takeIf { it != 0 }?.toString().orEmpty(),
                onValueChange = { input ->
                    if (input.length <= 3 && input.all(Char::isDigit)) {
                        vm.changeEditor(state.copy(draft = draft.copy(period = input.toIntOrNull() ?: 0)))
                    }
                }, label = { Text("Период, секунд (1–300)") }, singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("Меняйте параметры только если сервис их явно указал.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::save, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(8.dp)) else Text("Сохранить аккаунт")
        }
        Text("Сохраните резервные коды сервиса отдельно: после удаления приложения локальное хранилище восстановить нельзя.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SecretTextField(value: String, onValueChange: (String) -> Unit, label: String,
    visible: Boolean, onVisibilityChange: () -> Unit, enabled: Boolean) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), enabled = enabled,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = onVisibilityChange) {
            Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                if (visible) "Скрыть значение" else "Показать значение")
        } })
}
