package com.carry.app.persistence

import com.carry.app.test.IntegrationTestBase
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.vo.InvoiceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * ISSUED → OVERDUE 전이 규칙이 **두 곳에 존재**한다 — 도메인 전이표(`InvoiceStatus.canTransitionTo`)와
 * 조건부 UPDATE 의 `status = ISSUED` 가드(`InvoiceJpaRepository.markOverdueIfIssued`).
 *
 * 조건부 UPDATE 는 lost-update 방지(동시에 PAID 가 된 인보이스를 덮어쓰지 않기) 때문에 애그리거트를
 * 우회하므로 없앨 수 없다. 대신 **두 표현이 어긋나면 깨지는 테스트**로 묶어 둔다.
 * 전이표에 OVERDUE 를 허용하는 상태가 늘거나 줄면 여기서 잡힌다(불변식 카탈로그 §6.1-1).
 */
class InvoiceOverdueGuardIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired lateinit var invoicePersistencePort: InvoicePersistencePort

    private fun insertInvoice(orderId: Long, status: InvoiceStatus): Long = jdbc.queryForObject(
        """
        INSERT INTO payment_invoices (order_id, customer_id, status, weight, total_amount)
        VALUES (?, 1, ?, 5.00, 19500)
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        orderId,
        status.name,
    )!!

    private fun statusOf(id: Long): String =
        jdbc.queryForObject("SELECT status FROM payment_invoices WHERE id = ?", String::class.java, id)!!

    @BeforeEach
    @AfterEach
    fun clean() {
        jdbc.update("DELETE FROM payment_invoice_line_items")
        jdbc.update("DELETE FROM payment_invoices")
    }

    @Test
    fun `조건부 UPDATE 가 OVERDUE 로 바꾸는 상태는 전이표가 허용하는 상태와 정확히 일치한다`() {
        val now = Instant.parse("2026-09-18T00:00:00Z")

        InvoiceStatus.entries.forEachIndexed { index, status ->
            val id = insertInvoice(orderId = 1000L + index, status = status)

            val updated = invoicePersistencePort.markOverdueIfIssued(id, now)

            assertThat(updated)
                .describedAs("상태 %s: 전이표 허용=%s, 조건부 UPDATE 수행=%s", status, status.canTransitionTo(InvoiceStatus.OVERDUE), updated)
                .isEqualTo(status.canTransitionTo(InvoiceStatus.OVERDUE))
        }
    }

    @Test
    fun `이미 PAID 가 된 인보이스는 연체로 덮어써지지 않는다`() {
        // OverdueSweeper 가 조회한 뒤 결제가 완료되는 레이스 — 조건부 UPDATE 가 존재하는 이유다.
        val id = insertInvoice(orderId = 2000L, status = InvoiceStatus.PAID)

        val updated = invoicePersistencePort.markOverdueIfIssued(id, Instant.parse("2026-09-18T00:00:00Z"))

        assertThat(updated).isFalse()
        assertThat(statusOf(id)).isEqualTo("PAID")
    }

    @Test
    fun `ISSUED 인보이스는 연체로 확정되고 상태가 실제로 바뀐다`() {
        val id = insertInvoice(orderId = 3000L, status = InvoiceStatus.ISSUED)

        val updated = invoicePersistencePort.markOverdueIfIssued(id, Instant.parse("2026-09-18T00:00:00Z"))

        assertThat(updated).isTrue()
        assertThat(statusOf(id)).isEqualTo("OVERDUE")
    }
}
