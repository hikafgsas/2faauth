package com.example.authenticator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authenticator.core.OtpAccount
import com.example.authenticator.core.Totp

@Composable
fun AccountsScreen(vm: MainViewModel, onCopy: (OtpAccount) -> Unit) {
    val vault by vm.vault.collectAsStateWithLifecycle()
    val query by vm.search.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val failed by vm.vaultError.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<OtpAccount?>(null) }
    if (now <= 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val accounts = remember(vault.accounts, query) {
        vault.accounts.filter { it.details.issuer.contains(query.trim(), true) || it.details.label.contains(query.trim(), true) }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Коды для безопасного входа", Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(value = query, onValueChange = { vm.search.value = it.take(160) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            placeholder = { Text("Поиск аккаунта") }, singleLine = true, shape = RoundedCornerShape(18.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.search.value = "" }) {
                Icon(Icons.Outlined.Close, "Очистить поиск")
            } })
        if (failed) {
            Text("Не удалось открыть базу. Данные не сброшены.", Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::retryVault, modifier = Modifier.padding(start = 12.dp)) { Text("Повторить") }
        }
        if (vault.unreadableCount > 0) {
            Text("Не удалось расшифровать записей: ${vault.unreadableCount}. Они сохранены в базе. Подробности — в README.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
        }
        if (accounts.isEmpty() && !failed) {
            Box(Modifier.weight(1f)) {
                CenterMessage(if (query.isBlank()) "Добавьте первый аккаунт" else "Ничего не найдено",
                    if (query.isBlank()) "Нажмите «Добавить», отсканируйте QR-код сервиса или введите секретный ключ."
                    else "Попробуйте другое название сервиса или имя аккаунта.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("АККАУНТЫ · ${accounts.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(accounts, key = { it.id }) { account ->
                    AccountCard(account, now, { onCopy(account) }, { deleting = account })
                }
                item {
                    Text("Нажмите на карточку, чтобы скопировать код.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
    deleting?.let { account ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить аккаунт?") },
            text = { Text("${account.details.issuer} · ${account.details.label}\n\nДля повторного добавления потребуется исходный секретный ключ или новый QR-код сервиса.") },
            confirmButton = { TextButton(onClick = { vm.delete(account.id); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
    }
}

@Composable
private fun AccountCard(account: OtpAccount, now: Long, onCopy: () -> Unit, onDelete: () -> Unit) {
    val data = account.details
    val seconds = now / 1000
    val code = remember(data, seconds / data.period) {
        runCatching { Totp.generate(data.secret, seconds, data.digits, data.period, data.algorithm) }.getOrNull()
    }
    val grouped = code?.let { it.take(it.length / 2) + " " + it.drop(it.length / 2) } ?: "— — —"
    val remaining = Totp.remainingSeconds(now, data.period)
    val color = if (remaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onCopy, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text(data.issuer.take(1).uppercase(), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(data.issuer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(data.label, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Действия с аккаунтом") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Удалить аккаунт") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(grouped, Modifier.weight(1f), fontFamily = FontFamily.Monospace,
                    fontSize = if (data.digits == 8) 32.sp else 36.sp, fontWeight = FontWeight.Medium,
                    color = color, maxLines = 1)
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { Totp.remainingFraction(now, data.period) },
                        modifier = Modifier.fillMaxSize(), color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 3.dp)
                    Text(remaining.toString(), style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${data.algorithm.name} · ${data.period} сек.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
