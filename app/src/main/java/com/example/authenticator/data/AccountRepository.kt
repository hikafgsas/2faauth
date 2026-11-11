package com.example.authenticator.data

import com.example.authenticator.core.AccountDraft
import com.example.authenticator.core.OtpAccount
import com.example.authenticator.core.OtpAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class VaultSnapshot(val accounts: List<OtpAccount> = emptyList(), val unreadableCount: Int = 0)

class AccountRepository(private val dao: AccountDao, private val cipher: SecretCipher) {
    private val writeMutex = Mutex()

    fun observeAccounts(): Flow<VaultSnapshot> = dao.observeAll().map { rows ->
        val accounts = rows.mapNotNull { row -> runCatching { decrypt(row) }.getOrNull() }
        VaultSnapshot(accounts, rows.size - accounts.size)
    }.flowOn(Dispatchers.IO)

    suspend fun add(draft: AccountDraft) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val valid = draft.validated()
            val existing = dao.getAll()
            // Never add new records to a vault whose key may have been lost.
            val decoded = existing.map { row ->
                try { decrypt(row) } catch (_: Exception) {
                    throw IllegalStateException("Часть хранилища не читается. Сначала проверьте сохранённые данные.")
                }
            }
            require(decoded.none {
                it.details.secret == valid.secret && it.details.algorithm == valid.algorithm &&
                    it.details.digits == valid.digits && it.details.period == valid.period
            }) { "Такой ключ с этими параметрами уже добавлен." }
            val id = UUID.randomUUID().toString()
            val json = JSONObject().apply {
                put("issuer", valid.issuer)
                put("label", valid.label)
                put("secret", valid.secret)
                put("algorithm", valid.algorithm.name)
                put("digits", valid.digits)
                put("period", valid.period)
            }.toString().toByteArray(Charsets.UTF_8)
            try {
                dao.insert(AccountEntity(id, cipher.encrypt(id, json), System.currentTimeMillis()))
            } finally { json.fill(0) }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.delete(id) }

    private fun decrypt(row: AccountEntity): OtpAccount {
        val bytes = cipher.decrypt(row.id, row.encryptedPayload)
        try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            return OtpAccount(row.id, AccountDraft(
                issuer = json.getString("issuer"), label = json.getString("label"),
                secret = json.getString("secret"), algorithm = OtpAlgorithm.valueOf(json.getString("algorithm")),
                digits = json.getInt("digits"), period = json.getInt("period")
            ).validated())
        } finally { bytes.fill(0) }
    }
}
