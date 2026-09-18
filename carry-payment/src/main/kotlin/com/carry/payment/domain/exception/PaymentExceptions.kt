package com.carry.payment.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class InvoiceNotFoundException(identifier: String) : BusinessException(
    ErrorCode.INVOICE_NOT_FOUND,
    "청구서를 찾을 수 없습니다: $identifier",
)

/** 미저장 애그리거트(id=null)에서도 던질 수 있어야 하므로 식별자는 nullable 이다. */
class InvoiceAlreadyPaidException(invoiceId: Long?) : BusinessException(
    ErrorCode.INVOICE_ALREADY_PAID,
    "이미 결제된 청구서입니다: ${invoiceId ?: "미저장"}",
)

class PaymentNotFoundException(identifier: String) : BusinessException(
    ErrorCode.PAYMENT_NOT_FOUND,
    "결제를 찾을 수 없습니다: $identifier",
)

/** 미저장 애그리거트(id=null)에서도 던질 수 있어야 하므로 식별자는 nullable 이다. */
class PaymentAlreadyCompletedException(paymentId: Long?) : BusinessException(
    ErrorCode.PAYMENT_ALREADY_COMPLETED,
    "이미 완료된 결제입니다: ${paymentId ?: "미저장"}",
)

class PaymentGatewayException(message: String) : BusinessException(
    ErrorCode.PAYMENT_FAILED,
    "PG사 연동 오류: $message",
)

class InvoiceNotOwnedException(orderId: Long, requestingUserId: Long) : BusinessException(
    ErrorCode.INVOICE_NOT_OWNED,
    "청구서에 대한 권한이 없습니다: orderId=$orderId, userId=$requestingUserId",
)

class PaymentNotOwnedException(orderId: Long, requestingUserId: Long) : BusinessException(
    ErrorCode.PAYMENT_NOT_OWNED,
    "결제에 대한 권한이 없습니다: orderId=$orderId, userId=$requestingUserId",
)
