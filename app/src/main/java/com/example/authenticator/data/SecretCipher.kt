package com.example.authenticator.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The entire account payload is encrypted, with the Room row ID as authenticated data. */
class SecretCipher(private val alias: String = "authenticator.account.aes.v1") {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun encryptionKey(): SecretKey {
        existingKey()?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    private fun existingKey(): SecretKey? = keyStore.getKey(alias, null) as? SecretKey

    fun encrypt(rowId: String, plaintext: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey()) // Keystore generates a fresh random IV.
        cipher.updateAAD(rowId.toByteArray(Charsets.UTF_8))
        return "v1:${encode(cipher.iv)}:${encode(cipher.doFinal(plaintext))}"
    }

    fun decrypt(rowId: String, payload: String): ByteArray {
        val parts = payload.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Unknown encrypted payload" }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12) { "Invalid GCM IV" }
        val key = existingKey() ?: error("Keystore key is unavailable")
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(rowId.toByteArray(Charsets.UTF_8))
            doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

}
