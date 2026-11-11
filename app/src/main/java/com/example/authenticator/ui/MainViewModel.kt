package com.example.authenticator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.authenticator.AppContainer
import com.example.authenticator.core.AccountDraft
import com.example.authenticator.core.OtpAuthParser
import com.example.authenticator.data.AppSettings
import com.example.authenticator.data.ThemeMode
import com.example.authenticator.data.VaultSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { ACCOUNTS, ADD, SCAN, SETTINGS }
enum class AuthAction { UNLOCK, ENABLE_LOCK, DISABLE_LOCK }
data class EditorState(val draft: AccountDraft = AccountDraft(), val uri: String = "", val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    val settings = MutableStateFlow(AppSettings())
    val unlocked = MutableStateFlow(false)
    val screen = MutableStateFlow(Screen.ACCOUNTS)
    val editor = MutableStateFlow(EditorState())
    val search = MutableStateFlow("")
    val saving = MutableStateFlow(false)
    val vaultError = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)
    private var initialized = false
    private var backgrounded = false
    private var pendingUri: String? = null
    var pendingAuthAction: AuthAction? = null
        private set
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    val vault = unlocked.flatMapLatest { isOpen ->
        if (!isOpen) flowOf(VaultSnapshot()) else retry.flatMapLatest {
            vaultError.value = false
            container.accounts.observeAccounts().catch {
                vaultError.value = true
                emit(VaultSnapshot())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VaultSnapshot())

    val now = unlocked.flatMapLatest { isOpen ->
        if (!isOpen) flowOf(0L) else flow {
            while (true) {
                emit(System.currentTimeMillis().coerceAtLeast(0))
                delay(250)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), System.currentTimeMillis())

    init {
        viewModelScope.launch {
            container.settings.settings.collect {
                settings.value = it
                if (!initialized) {
                    initialized = true
                    unlocked.value = !it.appLock && !it.readError
                    consumePendingUri()
                }
                if (it.readError) lock()
                if (it.appLock && backgrounded) lock()
            }
        }
    }

    fun navigate(destination: Screen) {
        if (!unlocked.value) return
        if (destination == Screen.ADD && screen.value != Screen.SCAN) editor.value = EditorState()
        screen.value = destination
    }

    fun back() {
        screen.value = if (screen.value == Screen.SCAN) Screen.ADD else Screen.ACCOUNTS
        if (screen.value == Screen.ACCOUNTS && !saving.value) editor.value = EditorState()
    }

    fun changeEditor(value: EditorState) { if (!saving.value) editor.value = value.copy(error = null) }

    fun importUri(value: String, fromCamera: Boolean = false): Boolean {
        if (!unlocked.value || saving.value) return false
        val result = runCatching { OtpAuthParser.parse(value) }
        result.onSuccess {
            editor.value = EditorState(draft = it)
            screen.value = Screen.ADD // Always review before writing to the database.
        }.onFailure {
            val error = (it as? IllegalArgumentException)?.message ?: "Не удалось прочитать QR-код."
            if (fromCamera) message(error) else editor.value = editor.value.copy(error = error)
        }
        return result.isSuccess
    }

    fun receiveUri(value: String) {
        if (value.length > 8192) { message("Ссылка слишком длинная."); return }
        pendingUri = value
        consumePendingUri()
    }

    private fun consumePendingUri() {
        if (unlocked.value) pendingUri?.let { pendingUri = null; importUri(it) }
    }

    fun save() {
        if (!unlocked.value || saving.value) return
        val draft = try { editor.value.draft.validated() } catch (e: IllegalArgumentException) {
            editor.value = editor.value.copy(error = e.message)
            return
        }
        saving.value = true
        viewModelScope.launch {
            try {
                container.accounts.add(draft)
                editor.value = EditorState()
                screen.value = Screen.ACCOUNTS
                message("Аккаунт добавлен")
            } catch (e: CancellationException) { throw e
            } catch (e: IllegalArgumentException) {
                editor.value = editor.value.copy(error = e.message)
            } catch (e: IllegalStateException) {
                editor.value = editor.value.copy(error = "Не удалось сохранить аккаунт. Проверьте хранилище и повторите.")
            } catch (_: Exception) {
                editor.value = editor.value.copy(error = "Ошибка записи. Данные не были сброшены.")
            } finally { saving.value = false }
        }
    }

    fun delete(id: String) {
        if (!unlocked.value) return
        viewModelScope.launch {
            try { container.accounts.delete(id); message("Аккаунт удалён")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { message("Не удалось удалить аккаунт.") }
        }
    }

    fun enteredForeground() { backgrounded = false }
    fun lockWhenBackgrounded() {
        backgrounded = true
        if (settings.value.appLock) lock()
        pendingUri = null
    }

    private fun lock() {
        unlocked.value = false
        editor.value = EditorState()
        search.value = ""
        screen.value = Screen.ACCOUNTS
    }

    fun beginAuthentication(action: AuthAction) { pendingAuthAction = action }
    fun cancelAuthentication() { pendingAuthAction = null }

    fun authenticated() {
        val action = pendingAuthAction ?: return
        pendingAuthAction = null
        if (settings.value.readError || backgrounded) return
        when (action) {
            AuthAction.UNLOCK -> { unlocked.value = true; consumePendingUri() }
            AuthAction.ENABLE_LOCK -> updateSettings { container.settings.setAppLock(true) }
            AuthAction.DISABLE_LOCK -> updateSettings { container.settings.setAppLock(false) }
        }
    }

    fun setTheme(value: ThemeMode) = updateSettings { container.settings.setTheme(value) }
    fun setSecureScreen(value: Boolean) = updateSettings { container.settings.setSecureScreen(value) }
    private fun updateSettings(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { message("Не удалось сохранить настройку.") }
        }
    }
    fun retryVault() { retry.value += 1 }
    fun message(text: String) { messageChannel.trySend(text) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
