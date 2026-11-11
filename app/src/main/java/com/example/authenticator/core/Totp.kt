package com.example.authenticator.core

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class OtpAlgorithm(val macName: String) {
    SHA1("HmacSHA1"), SHA256("HmacSHA256"), SHA512("HmacSHA512")
}

/** RFC 6238, T0 = 0. Time is Unix time in seconds, independent of time zone. */
object Totp {
    fun generate(
        secret: String,
        epochSeconds: Long,
        digits: Int = 6,
        period: Int = 30,
        algorithm: OtpAlgorithm = OtpAlgorithm.SHA1
    ): String {
        require(epochSeconds >= 0)
        require(digits in 6..8)
        require(period in 1..300)
        val key = Base32.decode(secret)
        require(key.isNotEmpty())
        try {
            val counter = ByteBuffer.allocate(8).putLong(epochSeconds / period).array()
            val mac = Mac.getInstance(algorithm.macName)
            mac.init(SecretKeySpec(key, algorithm.macName))
            val hash = mac.doFinal(counter)
            val offset = hash.last().toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
            val modulo = when (digits) { 6 -> 1_000_000; 7 -> 10_000_000; else -> 100_000_000 }
            return (binary % modulo).toString().padStart(digits, '0')
        } finally {
            key.fill(0)
        }
    }

    fun remainingSeconds(epochMillis: Long, period: Int): Int {
        require(period in 1..300)
        require(epochMillis >= 0)
        return period - ((epochMillis / 1000) % period).toInt()
    }

    fun remainingFraction(epochMillis: Long, period: Int): Float =
        1f - (epochMillis % (period * 1000L)).toFloat() / (period * 1000L)
}
