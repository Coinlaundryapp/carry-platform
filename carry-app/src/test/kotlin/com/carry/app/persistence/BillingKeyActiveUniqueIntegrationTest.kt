package com.carry.app.persistence

import com.carry.app.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * "고객당 활성 빌링키 1개" 불변식이 **부분 유니크 인덱스로** 실제로 강제되는지 확인한다
 * (`uq_billing_keys_active_per_customer`, V26).
 *
 * 서비스 로직(재등록 시 기존 ACTIVE 를 INVALID 로 전환)에는 테스트가 있었지만, 그 로직이 무너졌을 때
 * DB 가 두 번째 ACTIVE 행을 실제로 거부하는지는 확인된 적이 없었다(불변식 카탈로그 §6.3-10).
 * 자동과금은 "고객의 활성 키" 를 단수로 전제하므로, 중복 ACTIVE 는 어떤 키로 긁을지 모르는 상태가 된다.
 */
class BillingKeyActiveUniqueIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var jdbc: JdbcTemplate

    private fun insertKey(customerId: Long, customerKey: String, status: String) {
        jdbc.update(
            """
            INSERT INTO customer_billing_keys
                (customer_id, customer_key, billing_key, card_company, card_last4, status)
            VALUES (?, ?, 'enc:dummy', '신한', '1234', ?)
            """.trimIndent(),
            customerId, customerKey, status,
        )
    }

    @BeforeEach
    @AfterEach
    fun clean() {
        jdbc.update("DELETE FROM customer_billing_keys")
    }

    @Test
    fun `같은 고객에게 두 번째 ACTIVE 빌링키는 들어가지 않는다`() {
        insertKey(customerId = 1L, customerKey = "ck-1", status = "ACTIVE")

        assertThatThrownBy { insertKey(customerId = 1L, customerKey = "ck-2", status = "ACTIVE") }
            .isInstanceOf(DuplicateKeyException::class.java)

        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer_billing_keys WHERE customer_id = 1 AND status = 'ACTIVE'",
                Int::class.java,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `INVALID 키는 몇 개든 남아 있을 수 있다 - 부분 인덱스라 이력이 보존된다`() {
        insertKey(customerId = 1L, customerKey = "ck-old-1", status = "INVALID")
        insertKey(customerId = 1L, customerKey = "ck-old-2", status = "INVALID")

        assertThatCode { insertKey(customerId = 1L, customerKey = "ck-new", status = "ACTIVE") }
            .doesNotThrowAnyException()

        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM customer_billing_keys WHERE customer_id = 1", Int::class.java),
        ).isEqualTo(3)
    }

    @Test
    fun `다른 고객끼리는 각자 ACTIVE 키를 가질 수 있다`() {
        insertKey(customerId = 1L, customerKey = "ck-a", status = "ACTIVE")

        assertThatCode { insertKey(customerId = 2L, customerKey = "ck-b", status = "ACTIVE") }
            .doesNotThrowAnyException()
    }
}
