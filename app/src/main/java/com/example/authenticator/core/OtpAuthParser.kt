package com.example.authenticator.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object OtpAuthParser {
    fun parse(value: String): AccountDraft {
        require(value.length <= 8192) { "Ссылка слишком длинная." }
        val uri = try { URI(value.trim()) } catch (_: Exception) {
            throw IllegalArgumentException("Некорректная otpauth-ссылка.")
        }
        require(uri.scheme.equals("otpauth", true)) { "Нужна ссылка вида otpauth://totp/…" }
        require(uri.host.equals("totp", true)) { "Поддерживается только TOTP; HOTP и ссылки миграции не поддерживаются." }
        require(uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null) {
            "Некорректный формат otpauth-ссылки."
        }
        val label = decode(uri.rawPath.orEmpty().removePrefix("/")).trim()
        require(label.isNotEmpty() && '/' !in label) { "В ссылке отсутствует корректное имя аккаунта." }
        val parameters = mutableMapOf<String, String>()
        uri.rawQuery.orEmpty().split('&').filter(String::isNotBlank).forEach { part ->
            val pair = part.split('=', limit = 2)
            val name = decode(pair[0]).lowercase(Locale.ROOT)
            require(name !in parameters) { "Параметр ссылки указан несколько раз." }
            parameters[name] = decode(pair.getOrElse(1) { "" })
        }
        val prefix = if (':' in label) label.substringBefore(':').trim() else ""
        val issuer = parameters["issuer"]?.trim().orEmpty()
        require(prefix.isEmpty() || issuer.isEmpty() || prefix == issuer) {
            "Название сервиса в метке и параметре issuer не совпадает."
        }
        val account = if (':' in label) label.substringAfter(':').trim() else label
        val algorithm = try {
            OtpAlgorithm.valueOf(parameters["algorithm"]?.uppercase(Locale.ROOT) ?: "SHA1")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Поддерживаются SHA1, SHA256 и SHA512.")
        }
        fun integer(name: String, default: Int): Int = parameters[name]?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException("Некорректный числовой параметр $name.")
        } ?: default
        return AccountDraft(
            issuer = issuer.ifEmpty { prefix.ifEmpty { account } },
            label = account,
            secret = parameters["secret"] ?: throw IllegalArgumentException("В ссылке отсутствует secret."),
            algorithm = algorithm,
            digits = integer("digits", 6),
            period = integer("period", 30)
        ).validated()
    }

    // URI encoding, not HTML form encoding: a literal '+' in an email stays '+'.
    private fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Некорректное кодирование символов в ссылке.")
    }
}
