package com.carry.payment.domain.exception

class InvoiceNotFoundException(identifier: String) :
    RuntimeException("청구서를 찾을 수 없습니다: $identifier")

class InvoiceAlreadyPaidException(invoiceId: Long) :
    RuntimeException("이미 결제된 청구서입니다: $invoiceId")

class PaymentNotFoundException(identifier: String) :
    RuntimeException("결제를 찾을 수 없습니다: $identifier")

class PaymentAlreadyCompletedException(paymentId: Long) :
    RuntimeException("이미 완료된 결제입니다: $paymentId")

class PaymentGatewayException(message: String) :
    RuntimeException("PG사 연동 오류: $message")
