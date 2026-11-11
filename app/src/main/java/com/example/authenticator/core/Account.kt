package com.example.authenticator.core

data class AccountDraft(
    val issuer: String = "",
    val label: String = "",
    val secret: String = "",
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30
) {
    fun validated(): AccountDraft {
        require(issuer.trim().length in 1..100) { "Укажите название сервиса (до 100 символов)." }
        require(label.trim().length in 1..160) { "Укажите имя аккаунта или почту (до 160 символов)." }
        require(digits in 6..8) { "Поддерживаются коды из 6, 7 или 8 цифр." }
        require(period in 1..300) { "Период должен быть от 1 до 300 секунд." }
        val bytes = Base32.decode(secret)
        try {
            require(bytes.size >= 10) { "Ключ слишком короткий: требуется не менее 80 бит (16 символов Base32)." }
        } finally {
            bytes.fill(0)
        }
        return copy(issuer = issuer.trim(), label = label.trim(), secret = Base32.normalize(secret))
    }
}

data class OtpAccount(val id: String, val details: AccountDraft)
