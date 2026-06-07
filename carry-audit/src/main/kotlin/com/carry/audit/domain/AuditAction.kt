package com.carry.audit.domain

/**
 * 감사 대상 민감 운영 작업.
 */
enum class AuditAction {
    ORDER_CANCEL,
    PAYMENT_REFUND,
    DISPATCH_ASSIGN,
    DISPATCH_REJECT_PENALTY,
}
