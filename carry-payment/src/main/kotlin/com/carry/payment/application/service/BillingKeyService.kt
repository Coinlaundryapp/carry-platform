package com.carry.payment.application.service

import com.carry.audit.domain.AuditAction
import com.carry.audit.port.AuditPort
import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.payment.application.port.inbound.BillingKeyUseCase
import com.carry.payment.application.port.inbound.BillingQueryUseCase
import com.carry.payment.application.port.outbound.BillingKeyPersistencePort
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.application.port.outbound.PaymentGatewayResolver
import com.carry.payment.application.port.outbound.PgBillingKeyRequest
import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.vo.PgProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class BillingKeyService(
    private val billingKeyPersistencePort: BillingKeyPersistencePort,
    private val paymentGatewayResolver: PaymentGatewayResolver,
    private val auditPort: AuditPort,
    private val clock: Clock,
    private val invoicePersistencePort: InvoicePersistencePort,
) : BillingKeyUseCase, BillingQueryUseCase {

    @Transactional
    override fun register(customerId: Long, authKey: String): BillingKey {
        val existing = billingKeyPersistencePort.findActiveByCustomerId(customerId)
        // customerKey 는 고객 최초 등록 시 1회 생성 — 이후 재등록에도 동일 키 재사용(토스 권장).
        // ⚠️ 이 재사용은 "활성 키가 항상 존재한다"는 전제 위에서만 안정적이다. 향후 무효화-후-미교체
        // 경로(Task 8+ 카드 해지 등)가 생기면, 여기서 상태 무관 최신 키(most-recent regardless of status)를
        // 조회해 customerKey 를 이어받도록 바꿔야 토스 customerKey 안정성 보장이 유지된다.
        val customerKey = existing?.customerKey ?: UUID.randomUUID().toString()

        val gateway = paymentGatewayResolver.resolve(PgProvider.TOSS_PAYMENTS)
        val result = gateway.issueBillingKey(PgBillingKeyRequest(authKey, customerKey))
        if (!result.success) {
            throw BusinessException(ErrorCode.BILLING_KEY_ISSUE_FAILED, result.failReason ?: "발급 거절")
        }
        // success=true 인데 billingKey 가 없으면 정상 발급으로 볼 수 없다 — raw NPE(→500) 대신 발급 실패로 처리.
        val issuedBillingKey = result.billingKey
            ?: throw BusinessException(ErrorCode.BILLING_KEY_ISSUE_FAILED, "PG 응답에 billingKey 가 없습니다")

        val now = clock.instant()
        // 부분 유니크 인덱스 (customer_id) WHERE status='ACTIVE' 는 statement 단위로 검사된다.
        // Hibernate 기본 flush 순서는 INSERT→UPDATE 라, 무효화(UPDATE)와 신규(INSERT)를 한 flush 에
        // 묶으면 새 ACTIVE 행 INSERT 가 먼저 나가 제약 위반이 난다. saveAndFlush 로 무효화를 먼저 확정한다.
        existing?.let {
            it.invalidate(now)
            billingKeyPersistencePort.saveAndFlush(it)
        }
        val saved = billingKeyPersistencePort.save(
            BillingKey.create(
                customerId, customerKey, result.billingKey!!,
                result.cardCompany ?: "UNKNOWN", result.cardLast4 ?: "0000", now,
            ),
        )

        auditPort.record(
            action = AuditAction.BILLING_KEY_REGISTERED,
            targetType = "BILLING_KEY",
            targetId = saved.id.toString(),
            before = existing?.id?.let { mapOf("invalidatedBillingKeyId" to it) },
            after = mapOf("customerId" to customerId, "cardLast4" to saved.cardLast4),
        )
        return saved
    }

    @Transactional(readOnly = true)
    override fun getActive(customerId: Long): BillingKey =
        billingKeyPersistencePort.findActiveByCustomerId(customerId)
            ?: throw BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND, "customerId=$customerId")

    @Transactional(readOnly = true)
    override fun hasActiveBillingKey(customerId: Long): Boolean =
        billingKeyPersistencePort.existsActiveByCustomerId(customerId)

    @Transactional(readOnly = true)
    override fun hasOverdueInvoice(customerId: Long): Boolean =
        invoicePersistencePort.existsOverdueByCustomerId(customerId)
}
