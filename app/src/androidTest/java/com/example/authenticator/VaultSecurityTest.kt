package com.example.authenticator

import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.authenticator.core.AccountDraft
import com.example.authenticator.data.AccountRepository
import com.example.authenticator.data.SecretCipher
import com.example.authenticator.data.VaultDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VaultSecurityTest {
    private lateinit var database: VaultDatabase
    private lateinit var cipher: SecretCipher
    private val alias = "authenticator.instrumentation.${UUID.randomUUID()}"

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
        cipher = SecretCipher(alias)
    }

    @After fun tearDown() {
        database.close()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test fun randomIvRoundTripAndRowBinding() {
        val bytes = "private account payload".toByteArray()
        val first = cipher.encrypt("row-a", bytes)
        val second = cipher.encrypt("row-a", bytes)
        assertNotEquals(first, second)
        assertArrayEquals(bytes, cipher.decrypt("row-a", first))
        try { cipher.decrypt("row-b", first); fail("AAD mismatch was accepted") }
        catch (_: GeneralSecurityException) { }
    }

    @Test fun tamperingIsDetected() {
        val parts = cipher.encrypt("row", "payload".toByteArray()).split(':').toMutableList()
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        parts[2] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        try { cipher.decrypt("row", parts.joinToString(":")); fail("Tampering was accepted") }
        catch (_: GeneralSecurityException) { }
    }

    @Test fun missingKeyIsNotSilentlyRegenerated() {
        val encrypted = cipher.encrypt("row", "payload".toByteArray())
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(alias)
        try { cipher.decrypt("row", encrypted); fail("Missing key was ignored") }
        catch (_: IllegalStateException) { }
        assertFalse(keyStore.containsAlias(alias))
    }

    @Test fun roomRoundTripEncryptedPayloadDuplicateAndDelete() = runBlocking {
        val repo = AccountRepository(database.accountDao(), cipher)
        val draft = AccountDraft("Private service", "private@example.test", "JBSWY3DPEHPK3PXP")
        repo.add(draft)
        val row = database.accountDao().getAll().single()
        assertFalse(row.encryptedPayload.contains(draft.secret))
        assertFalse(row.encryptedPayload.contains(draft.label))
        val snapshot = repo.observeAccounts().first()
        assertEquals(draft, snapshot.accounts.single().details)
        assertEquals(0, snapshot.unreadableCount)
        try { repo.add(draft); fail("Duplicate was accepted") }
        catch (_: IllegalArgumentException) { }
        repo.delete(row.id)
        assertTrue(repo.observeAccounts().first().accounts.isEmpty())
    }
}
