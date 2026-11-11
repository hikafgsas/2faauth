package com.example.authenticator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.authenticator.core.OtpAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorRoot(vm: MainViewModel, onAuth: (AuthAction) -> Unit, onCopy: (OtpAccount) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = unlocked && screen != Screen.ACCOUNTS) { vm.back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(when {
                    !unlocked -> "Authenticator"
                    screen == Screen.ADD -> "Новый аккаунт"
                    screen == Screen.SCAN -> "Сканирование QR"
                    screen == Screen.SETTINGS -> "Настройки"
                    else -> "Мои коды"
                }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (unlocked && screen in setOf(Screen.ADD, Screen.SCAN)) {
                        IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") }
                    } else {
                        Icon(Icons.Outlined.Shield, null, Modifier.padding(start = 20.dp, end = 8.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (unlocked && screen in setOf(Screen.ACCOUNTS, Screen.SETTINGS)) {
                NavigationBar {
                    NavigationBarItem(selected = screen == Screen.ACCOUNTS, onClick = { vm.navigate(Screen.ACCOUNTS) },
                        icon = { Icon(Icons.Outlined.Key, null) }, label = { Text("Коды") })
                    NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { vm.navigate(Screen.SETTINGS) },
                        icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Настройки") })
                }
            }
        },
        floatingActionButton = {
            if (settings.ready && unlocked && screen == Screen.ACCOUNTS) {
                ExtendedFloatingActionButton(onClick = { vm.navigate(Screen.ADD) },
                    icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Добавить") })
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                !settings.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                settings.readError -> CenterMessage("Не удалось прочитать настройки",
                    "Хранилище осталось закрытым. Перезапустите приложение; не очищайте данные, если в нём есть важные аккаунты.")
                !unlocked -> CenterMessage("Ваши коды под защитой", "Подтвердите личность, чтобы открыть аккаунты.") {
                    Button(onClick = { onAuth(AuthAction.UNLOCK) }) {
                        Icon(Icons.Outlined.Fingerprint, null)
                        Text("Разблокировать", Modifier.padding(start = 8.dp))
                    }
                }
                screen == Screen.ACCOUNTS -> AccountsScreen(vm, onCopy)
                screen == Screen.ADD -> AddAccountScreen(vm)
                screen == Screen.SCAN -> ScannerScreen(onDetected = { vm.importUri(it, fromCamera = true) })
                screen == Screen.SETTINGS -> SettingsScreen(settings, vm::setTheme, vm::setSecureScreen) {
                    onAuth(if (settings.appLock) AuthAction.DISABLE_LOCK else AuthAction.ENABLE_LOCK)
                }
            }
        }
    }
}

@Composable
internal fun CenterMessage(title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(24.dp))
        action()
    }
}
