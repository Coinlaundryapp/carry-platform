package com.carry.payment.adapter.outbound.persistence

import com.carry.payment.adapter.outbound.persistence.entity.InvoiceJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.InvoiceJpaRepository
import com.carry.payment.application.port.outbound.InvoicePersistencePort
import com.carry.payment.domain.model.Invoice
import com.carry.payment.domain.vo.InvoiceStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class InvoicePersistenceAdapter(
    private val invoiceJpaRepository: InvoiceJpaRepository,
) : InvoicePersistencePort {

    override fun save(invoice: Invoice): Invoice {
        val entity = if (invoice.id == null) {
            InvoiceJpaEntity.fromDomain(invoice)
        } else {
            val existing = invoiceJpaRepository.getReferenceById(invoice.id)
            existing.updateFrom(invoice)
            existing
        }
        return invoiceJpaRepository.save(entity).toDomain()
    }

    // 읽기 전용 트랜잭션 필수: lineItems 는 LAZY 컬렉션이라 toDomain() 의 map 이 이 메서드 안에서
    // 세션이 열려 있어야 한다. 이 어댑터를 이미 @Transactional 인 호출자(AutoChargeService 등)에서만
    // 부르는 동안은 우연히 동작했지만, OverdueSweeper 처럼 트랜잭션 밖(스케줄러)에서 직접 호출하면
    // LazyInitializationException 이 난다 — 호출자 트랜잭션 유무와 무관하게 이 어댑터가 자급자족해야 한다.
    @Transactional(readOnly = true)
    override fun findById(id: Long): Invoice? {
        return invoiceJpaRepository.findById(id).orElse(null)?.toDomain()
    }

    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: Long): Invoice? {
        return invoiceJpaRepository.findByOrderId(orderId)?.toDomain()
    }

    @Transactional(readOnly = true)
    override fun findIssuedBefore(cutoff: Instant): List<Invoice> {
        return invoiceJpaRepository.findByStatusAndCreatedAtBefore(InvoiceStatus.ISSUED, cutoff).map { it.toDomain() }
    }

    override fun existsOverdueByCustomerId(customerId: Long): Boolean {
        return invoiceJpaRepository.existsByCustomerIdAndStatus(customerId, InvoiceStatus.OVERDUE)
    }

    @Transactional
    override fun markOverdueIfIssued(invoiceId: Long, now: Instant): Boolean {
        return invoiceJpaRepository.markOverdueIfIssued(invoiceId, now) == 1
    }
}
