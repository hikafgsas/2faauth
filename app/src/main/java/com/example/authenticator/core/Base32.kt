package com.example.authenticator.core

import java.util.Locale

/** RFC 4648 Base32. Separators are whitespace; ambiguous 0/1 are never substituted. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun normalize(value: String): String {
        val compact = value.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)
        require(compact.length <= 2048) { "Секретный ключ слишком длинный." }
        val content = compact.trimEnd('=')
        require(content.isNotEmpty() && content.all { it in ALPHABET }) {
            "Ключ должен содержать только буквы A–Z и цифры 2–7 (Base32)."
        }
        require(content.length % 8 in setOf(0, 2, 4, 5, 7)) { "Некорректная длина Base32-ключа." }
        if (compact.length != content.length) {
            require(compact.length % 8 == 0 && compact.length - content.length < 8) {
                "Некорректное дополнение Base32-ключа."
            }
        }
        return content
    }

    fun decode(value: String): ByteArray {
        val text = normalize(value)
        val output = ByteArray(text.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in text) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(char)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output[index++] = (buffer ushr bits).toByte()
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        require(buffer == 0) { "Ненулевые завершающие биты Base32-ключа." }
        return output
    }
}
