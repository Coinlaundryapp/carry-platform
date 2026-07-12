package com.carry.payment.adapter.inbound.rest.dto

import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.model.Payment
import com.carry.payment.domain.vo.InvoiceLineItem
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "청구서 응답")
data class InvoiceResponse(
    @Schema(description = "청구서 ID") val id: Long,
    @Schema(description = "주문 ID") val orderId: Long,
    @Schema(description = "고객 ID") val customerId: Long,
    @Schema(description = "청구서 상태") val status: String,
    @Schema(description = "청구서 항목 목록") val lineItems: List<InvoiceLineItemResponse>,
    @Schema(description = "무게(kg)") val weight: BigDecimal,
    @Schema(description = "총 금액(원)") val totalAmount: Long,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(invoice: Invoice) = InvoiceResponse(
            id = invoice.id!!,
            orderId = invoice.orderId,
            customerId = invoice.customerId,
            status = invoice.status.name,
            lineItems = invoice.lineItems.map { InvoiceLineItemResponse.from(it) },
            weight = invoice.weight,
            totalAmount = invoice.totalAmount,
            createdAt = invoice.createdAt,
        )
    }
}

@Schema(description = "청구서 항목")
data class InvoiceLineItemResponse(
    @Schema(description = "요금 유형") val chargeType: String,
    @Schema(description = "설명") val description: String,
    @Schema(description = "금액(원)") val amount: Long,
) {
    companion object {
        fun from(item: InvoiceLineItem) = InvoiceLineItemResponse(
            chargeType = item.chargeType.name,
            description = item.description,
            amount = item.amount,
        )
    }
}

@Schema(description = "결제 응답")
data class PaymentResponse(
    @Schema(description = "결제 ID") val id: Long,
    @Schema(description = "청구서 ID") val invoiceId: Long,
    @Schema(description = "주문 ID") val orderId: Long,
    @Schema(description = "고객 ID") val customerId: Long,
    @Schema(description = "결제 상태") val status: String,
    @Schema(description = "PG사") val pgProvider: String,
    @Schema(description = "PG 거래 ID", nullable = true) val pgTransactionId: String?,
    @Schema(description = "결제 금액(원)") val amount: Long,
    @Schema(description = "결제 시간", nullable = true) val paidAt: Instant?,
    @Schema(description = "실패 사유", nullable = true) val failReason: String?,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id!!,
            invoiceId = payment.invoiceId,
            orderId = payment.orderId,
            customerId = payment.customerId,
            status = payment.status.name,
            pgProvider = payment.pgProvider.name,
            pgTransactionId = payment.pgTransactionId,
            amount = payment.amount,
            paidAt = payment.paidAt,
            failReason = payment.failReason,
            createdAt = payment.createdAt,
        )
    }
}

@Schema(description = "빌링키 등록 요청")
data class BillingKeyRegisterRequest(
    @Schema(description = "PG SDK 카드 등록창 결과 authKey", example = "auth_key_from_toss_sdk")
    @field:NotBlank
    val authKey: String,
)

@Schema(description = "빌링키 등록 응답 — 카드 마스킹 정보만 제공, 원본 billingKey/customerKey 는 노출하지 않는다")
data class BillingKeyResponse(
    @Schema(description = "카드사") val cardCompany: String,
    @Schema(description = "카드 뒷 4자리") val cardLast4: String,
    @Schema(description = "등록 시각") val registeredAt: Instant,
) {
    companion object {
        fun from(billingKey: BillingKey) = BillingKeyResponse(
            cardCompany = billingKey.cardCompany,
            cardLast4 = billingKey.cardLast4,
            registeredAt = billingKey.createdAt,
        )
    }
}
