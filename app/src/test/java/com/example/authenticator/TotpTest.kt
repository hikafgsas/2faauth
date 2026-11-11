package com.example.authenticator

import com.example.authenticator.core.OtpAlgorithm
import com.example.authenticator.core.Totp
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    @Test fun allRfc6238AppendixBVectors() {
        val times = listOf(59L, 1_111_111_109L, 1_111_111_111L, 1_234_567_890L, 2_000_000_000L, 20_000_000_000L)
        val vectors = listOf(
            Triple(OtpAlgorithm.SHA1, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
                listOf("94287082", "07081804", "14050471", "89005924", "69279037", "65353130")),
            Triple(OtpAlgorithm.SHA256, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA",
                listOf("46119246", "68084774", "67062674", "91819424", "90698825", "77737706")),
            Triple(OtpAlgorithm.SHA512,
                "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA",
                listOf("90693936", "25091201", "99943326", "93441116", "38618901", "47863826"))
        )
        for ((algorithm, secret, expected) in vectors) {
            times.forEachIndexed { i, time -> assertEquals("$algorithm at $time", expected[i], Totp.generate(secret, time, 8, 30, algorithm)) }
        }
    }

    @Test fun sixDigitsAndTimeBoundary() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals("287082", Totp.generate(secret, 59))
        assertEquals(Totp.generate(secret, 30), Totp.generate(secret, 59))
        assertEquals(1, Totp.remainingSeconds(59_999, 30))
        assertEquals(30, Totp.remainingSeconds(60_000, 30))
        assertEquals(1f, Totp.remainingFraction(60_000, 30), 0.00001f)
    }

    @Test fun nonDefaultPeriodUsesItsOwnCounter() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals(Totp.generate(secret, 59, period = 30), Totp.generate(secret, 119, period = 60))
        assertEquals(60, Totp.remainingSeconds(120_000, 60))
    }
}
