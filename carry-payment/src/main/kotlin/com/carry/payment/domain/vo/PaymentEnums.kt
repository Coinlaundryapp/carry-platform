package com.carry.payment.domain.vo

enum class InvoiceStatus {
    ISSUED, PAID, OVERDUE, CANCELLED, REFUNDED;

    fun canTransitionTo(target: InvoiceStatus): Boolean = when (this) {
        ISSUED -> target in listOf(PAID, OVERDUE, CANCELLED)
        // OVERDUE: 미수금 확정(신규 주문 차단) — 재과금은 계속되므로 PAID 로 회복 가능
        OVERDUE -> target in listOf(PAID, CANCELLED)
        PAID -> target == REFUNDED
        CANCELLED -> false
        REFUNDED -> false
    }
}

enum class PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUND_PENDING, REFUNDED;

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target in listOf(COMPLETED, FAILED)
        // 환불은 의도 표시(REFUND_PENDING) 후 PG 취소 성공 시 REFUNDED 로 — PG 장애 시 재시도 가능하게 단계 분리.
        COMPLETED -> target == REFUND_PENDING
        REFUND_PENDING -> target == REFUNDED
        FAILED -> target == PENDING
        REFUNDED -> false
    }
}

enum class PgProvider {
    TOSS_PAYMENTS,
}

/** 빌링키 상태 — 재등록 시 기존 ACTIVE 키를 INVALID 로 전환한다(고객당 활성 키 1개). */
enum class BillingKeyStatus {
    ACTIVE, INVALID,
}

enum class ChargeType {
    LAUNDRY_PRICE, DELIVERY_FEE, SERVICE_FEE,
}

/** 원장 기입의 거래 그룹 유형 — REFUND 는 PAYMENT 행들의 정확한 역분개. */
enum class LedgerEntryType {
    PAYMENT, REFUND,
}

/**
 * 정산 계정 타입. 세탁소는 수취인이 아니다 — 캐리어가 코인세탁소 기계에 현금을 직접
 * 투입하고 수행하는 모델이라, 세탁비는 캐리어에게 변제(reimbursement)된다(2026-07-12 확정).
 */
enum class LedgerAccountType {
    CUSTOMER, CARRIER, PLATFORM,
}

/** PG 대사(reconciliation)에서 감지하는 불일치 유형 — 유형별로 운영 대응 절차가 다르다. */
enum class ReconciliationMismatchType {
    /** PG 과금인데 로컬 결제가 없거나 완료 상태가 아님 — 고객 돈이 나갔는데 서비스 미제공 위험. */
    ORPHAN_PG_CHARGE,

    /** 로컬 COMPLETED 인데 PG 과금 기록 없음 — 미수금 위험. */
    MISSING_IN_PG,

    /** 로컬-PG 과금 금액 불일치. */
    AMOUNT_MISMATCH,

    /** 로컬 REFUNDED 인데 PG 취소 기록 없음 — 환불 미집행 위험. */
    MISSING_PG_CANCEL,

    /** PG 취소 완료인데 로컬이 REFUNDED 아님 — REFUND_PENDING 이면 스위퍼가 PG 를 재호출하기 전 수렴 대상(P2b). */
    PG_CANCEL_NOT_MARKED,
}
