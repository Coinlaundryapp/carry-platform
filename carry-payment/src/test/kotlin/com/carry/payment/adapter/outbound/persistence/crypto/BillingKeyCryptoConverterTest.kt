package com.carry.payment.adapter.outbound.persistence.crypto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class BillingKeyCryptoConverterTest {
    // 32바이트 테스트 키
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val converter = BillingKeyCryptoConverter(key)

    @Test
    fun `암호화-복호화 왕복이 원문을 보존한다`() {
        val cipher = converter.convertToDatabaseColumn("bk-secret-123")
        assertThat(cipher).isNotEqualTo("bk-secret-123")
        assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo("bk-secret-123")
    }

    @Test
    fun `같은 평문도 IV 랜덤으로 매번 다른 암호문`() {
        val a = converter.convertToDatabaseColumn("bk")
        val b = converter.convertToDatabaseColumn("bk")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `null 은 null 로 통과`() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun `16바이트 키는 AES-256 위반이라 생성 시점에 거부한다`() {
        val undersizedKey = Base64.getEncoder().encodeToString(ByteArray(16))
        assertThatThrownBy { BillingKeyCryptoConverter(undersizedKey) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
