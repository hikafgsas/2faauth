package com.example.authenticator

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.authenticator.data.AccountRepository
import com.example.authenticator.data.SecretCipher
import com.example.authenticator.data.SettingsStore
import com.example.authenticator.data.VaultDatabase

class AuthenticatorApplication : Application() {
    val container by lazy { AppContainer(this) }
    val clipboard by lazy { SensitiveClipboard(this) }
}

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db").build()
    val accounts = AccountRepository(database.accountDao(), SecretCipher())
    val settings = SettingsStore(context)
}
