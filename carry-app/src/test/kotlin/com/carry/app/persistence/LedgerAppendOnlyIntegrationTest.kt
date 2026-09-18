package com.carry.app.persistence

import com.carry.app.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 정산 원장 append-only 가 **DB 에서** 강제되는지 확인한다(V31 트리거).
 *
 * 도메인·어댑터 쪽 보호(전 필드 val, 수정 메서드 없음, 리포지토리 연산 축소)는 코드를 고치면 함께
 * 무너진다. 금액은 되돌릴 수 없고 사후 감사 대상이므로 마지막 방어선은 DB 여야 한다.
 * 역분개(새 행 추가)는 열려 있어야 하므로 INSERT 가 여전히 되는 것까지 함께 단언한다.
 */
class LedgerAppendOnlyIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var jdbc: JdbcTemplate

    private fun insertEntry(amount: Long): Long = jdbc.queryForObject(
        """
        INSERT INTO payment_ledger_entries
            (payment_id, order_id, entry_type, account_type, account_id, charge_type, amount)
        VALUES (1, 1, 'PAYMENT', 'CUSTOMER', 1, NULL, ?)
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        amount,
    )!!

    @BeforeEach
    @AfterEach
    fun clean() {
        // DELETE 가 막히므로 격리도 TRUNCATE 로 한다(행 트리거는 TRUNCATE 에 반응하지 않는다).
        jdbc.execute("TRUNCATE TABLE payment_ledger_entries CASCADE")
    }

    @Test
    fun `기입된 원장 행은 UPDATE 할 수 없다`() {
        val id = insertEntry(-19500L)

        assertThatThrownBy { jdbc.update("UPDATE payment_ledger_entries SET amount = 0 WHERE id = ?", id) }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")

        assertThat(
            jdbc.queryForObject("SELECT amount FROM payment_ledger_entries WHERE id = ?", Long::class.java, id),
        ).isEqualTo(-19500L)
    }

    @Test
    fun `기입된 원장 행은 DELETE 할 수 없다`() {
        val id = insertEntry(-19500L)

        assertThatThrownBy { jdbc.update("DELETE FROM payment_ledger_entries WHERE id = ?", id) }
            .isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("append-only")

        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM payment_ledger_entries", Int::class.java),
        ).isEqualTo(1)
    }

    @Test
    fun `역분개는 새 행 추가로 여전히 가능하다`() {
        insertEntry(-19500L)
        insertEntry(19500L)

        assertThat(
            jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payment_ledger_entries", Long::class.java),
        ).isZero()
    }
}
