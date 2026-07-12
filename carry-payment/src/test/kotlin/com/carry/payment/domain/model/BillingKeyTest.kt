package com.carry.payment.domain.model

import com.carry.payment.domain.vo.BillingKeyStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class BillingKeyTest {
    private val now = Instant.parse("2026-07-12T00:00:00Z")

    private fun create() = BillingKey.create(
        customerId = 1L, customerKey = "ck-uuid", billingKey = "bk-secret",
        cardCompany = "STUB카드", cardLast4 = "0000", now = now,
    )

    @Test
    fun `생성 시 ACTIVE 상태다`() {
        assertThat(create().status).isEqualTo(BillingKeyStatus.ACTIVE)
    }

    @Test
    fun `invalidate 하면 INVALID 전이 + 시각 기록`() {
        val key = create()
        key.invalidate(now.plusSeconds(60))
        assertThat(key.status).isEqualTo(BillingKeyStatus.INVALID)
        assertThat(key.invalidatedAt).isEqualTo(now.plusSeconds(60))
    }

    @Test
    fun `이미 INVALID 인 키를 다시 invalidate 하면 예외`() {
        val key = create()
        key.invalidate(now)
        assertThatThrownBy { key.invalidate(now) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `card last4는 4자리가 아니면 생성 거부`() {
        assertThatThrownBy {
            BillingKey.create(1L, "ck", "bk", "카드", "00000", now)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
