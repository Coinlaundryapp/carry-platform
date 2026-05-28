package com.carry.payment.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class InvoiceNotFoundException(identifier: String) : BusinessException(
    ErrorCode.INVOICE_NOT_FOUND,
    "청구서를 찾을 수 없습니다: $identifier",
)

class InvoiceAlreadyPaidException(invoiceId: Long) : BusinessException(
    ErrorCode.INVOICE_ALREADY_PAID,
    "이미 결제된 청구서입니다: $invoiceId",
)

class PaymentNotFoundException(identifier: String) : BusinessException(
    ErrorCode.PAYMENT_NOT_FOUND,
    "결제를 찾을 수 없습니다: $identifier",
)

class PaymentAlreadyCompletedException(paymentId: Long) : BusinessException(
    ErrorCode.PAYMENT_ALREADY_COMPLETED,
    "이미 완료된 결제입니다: $paymentId",
)

class PaymentGatewayException(message: String) : BusinessException(
    ErrorCode.PAYMENT_FAILED,
    "PG사 연동 오류: $message",
)
