package com.carry.payment.domain.model

import com.carry.common.exception.checkInvariant
import com.carry.payment.domain.vo.ChargeType
import com.carry.payment.domain.vo.LedgerAccountType
import com.carry.payment.domain.vo.LedgerEntryType

/**
 * append-only 정산 원장의 기입 한 행. 정산 근거가 "재계산"이 아닌 "기록"이 되도록,
 * 돈이 움직인 사실을 부호 있는 금액으로 남긴다(원 단위, 원행 불변·수정 없음).
 */
data class LedgerEntry(
    val paymentId: Long,
    val orderId: Long,
    val entryType: LedgerEntryType,
    val accountType: LedgerAccountType,
    /** CUSTOMER=customerId, CARRIER=carrierId. PLATFORM 은 단일 주체라 null. */
    val accountId: Long?,
    /** 수취 행의 근거 라인아이템. CUSTOMER 총액 차변 행은 null. */
    val chargeType: ChargeType?,
    val amount: Long,
)

/**
 * 거래 그룹(결제 1건/환불 1건) 단위의 균형 기입 팩토리 — 그룹 내 Σamount = 0 을 강제한다.
 *
 * 분배(2026-07-12 확정): LAUNDRY_PRICE·DELIVERY_FEE → CARRIER(기계 투입 현금 변제 + 배달
 * 수고비), SERVICE_FEE → PLATFORM, CUSTOMER 는 총액 차변. 세탁소 계정은 없다.
 */
object LedgerEntries {

    fun forPayment(payment: Payment, invoice: Invoice, carrierId: Long?): List<LedgerEntry> =
        balanced(buildGroup(payment, invoice, carrierId, LedgerEntryType.PAYMENT, sign = +1))

    /** 전액 환불의 역분개 — PAYMENT 그룹과 부호만 반대인 행들. */
    fun forRefund(payment: Payment, invoice: Invoice, carrierId: Long?): List<LedgerEntry> =
        balanced(buildGroup(payment, invoice, carrierId, LedgerEntryType.REFUND, sign = -1))

    private fun buildGroup(
        payment: Payment,
        invoice: Invoice,
        carrierId: Long?,
        entryType: LedgerEntryType,
        sign: Int,
    ): List<LedgerEntry> = buildList {
        add(
            LedgerEntry(
                paymentId = payment.id!!, orderId = payment.orderId, entryType = entryType,
                accountType = LedgerAccountType.CUSTOMER, accountId = payment.customerId,
                chargeType = null, amount = -sign * invoice.totalAmount,
            ),
        )
        invoice.lineItems.forEach { item ->
            val (accountType, accountId) = when (item.chargeType) {
                ChargeType.LAUNDRY_PRICE, ChargeType.DELIVERY_FEE -> LedgerAccountType.CARRIER to carrierId
                ChargeType.SERVICE_FEE -> LedgerAccountType.PLATFORM to null
            }
            add(
                LedgerEntry(
                    paymentId = payment.id!!, orderId = payment.orderId, entryType = entryType,
                    accountType = accountType, accountId = accountId,
                    chargeType = item.chargeType, amount = sign * item.amount,
                ),
            )
        }
    }

    private fun balanced(entries: List<LedgerEntry>): List<LedgerEntry> {
        val sum = entries.sumOf { it.amount }
        // 입력 검증(4xx)이 아니라 내부 일관성이다 — 여기까지 왔는데 Σ≠0 이면 산식이 틀린 것이므로 500.
        checkInvariant(sum == 0L) {
            "원장 거래 그룹이 균형이 아닙니다(Σ=$sum) — invoice 총액과 라인아이템 합계 불일치"
        }
        return entries
    }
}
