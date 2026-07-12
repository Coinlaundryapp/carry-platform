package com.carry.payment.adapter.outbound.persistence.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * billing_key 컬럼 AES-256-GCM 암호화. 포맷: Base64(IV(12) || ciphertext+tag).
 * 키는 carry.payment.billing-key-enc-key (Base64 32바이트). 운영에선 환경변수 주입 전제 —
 * 코드 기본값은 로컬·테스트 전용이다.
 * Spring Boot 는 Hibernate SpringBeanContainer 를 자동 구성하므로 @Component 컨버터에 생성자 주입이 동작한다.
 */
@Component
@Converter
class BillingKeyCryptoConverter(
    @Value("\${carry.payment.billing-key-enc-key:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=}")
    keyBase64: String,
) : AttributeConverter<String?, String?> {

    private val key = SecretKeySpec(Base64.getDecoder().decode(keyBase64), "AES")
    private val random = SecureRandom()

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val bytes = Base64.getDecoder().decode(dbData)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
