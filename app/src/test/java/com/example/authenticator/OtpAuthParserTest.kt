package com.example.authenticator

import com.example.authenticator.core.AccountDraft
import com.example.authenticator.core.Base32
import com.example.authenticator.core.OtpAlgorithm
import com.example.authenticator.core.OtpAuthParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OtpAuthParserTest {
    private val secret = "JBSWY3DPEHPK3PXP"

    @Test fun parsesDefaultsAndPercentEncoding() {
        val a = OtpAuthParser.parse("otpauth://totp/Example%3Aalice%40example.com?secret=$secret&issuer=Example")
        assertEquals("Example", a.issuer)
        assertEquals("alice@example.com", a.label)
        assertEquals(30, a.period)
        assertEquals(6, a.digits)
        assertEquals(OtpAlgorithm.SHA1, a.algorithm)
    }

    @Test fun preservesLiteralPlusAndExtendedParameters() {
        val a = OtpAuthParser.parse("otpauth://totp/A:alice+tag%40example.com?secret=$secret&issuer=A&algorithm=SHA256&digits=8&period=60")
        assertEquals("alice+tag@example.com", a.label)
        assertEquals(OtpAlgorithm.SHA256, a.algorithm)
        assertEquals(8, a.digits)
        assertEquals(60, a.period)
    }

    @Test fun rejectsUnsupportedOrAmbiguousLinks() {
        listOf(
            "otpauth://hotp/A:b?secret=$secret&counter=0",
            "https://totp/A:b?secret=$secret",
            "otpauth://totp/A:b?secret=$secret&issuer=B",
            "otpauth://totp/A:b?secret=$secret&secret=$secret",
            "otpauth://totp/A:b?secret=$secret&digits=9",
            "otpauth://totp/A:b?secret=$secret&period=0",
            "otpauth://totp/A:b?secret=$secret&algorithm=MD5",
            "otpauth://totp/A:b?secret=$secret&period=hello",
            "otpauth://totp/A:b?issuer=A",
            "otpauth://totp/A:b?secret=$secret#fragment"
        ).forEach { link -> assertThrows(IllegalArgumentException::class.java) { OtpAuthParser.parse(link) } }
    }

    @Test fun base32NormalizationAndStrictValidation() {
        assertEquals(secret, Base32.normalize("jbsw y3dp ehpk 3pxp"))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW6==="))
        listOf("A", "ABC", "MZ", "MZXW7===", "AB0D", "M=ZXW6", "MZXW6=").forEach {
            assertThrows(IllegalArgumentException::class.java) { Base32.decode(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { AccountDraft("A", "b", "MY").validated() }
    }
}
