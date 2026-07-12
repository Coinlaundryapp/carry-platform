package com.carry.payment.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.payment.adapter.outbound.persistence.crypto.BillingKeyCryptoConverter
import com.carry.payment.domain.model.BillingKey
import com.carry.payment.domain.vo.BillingKeyStatus
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "customer_billing_keys")
class BillingKeyJpaEntity(
    @Column(nullable = false)
    val customerId: Long,

    @Column(nullable = false, length = 64)
    val customerKey: String,

    @Convert(converter = BillingKeyCryptoConverter::class)
    @Column(nullable = false, length = 512)
    val billingKey: String,

    @Column(nullable = false, length = 50)
    val cardCompany: String,

    @Column(nullable = false, length = 4)
    val cardLast4: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BillingKeyStatus,

    var invalidatedAt: Instant?,
) : BaseEntity() {

    fun toDomain(): BillingKey = BillingKey.reconstitute(
        id = id,
        customerId = customerId,
        customerKey = customerKey,
        billingKey = billingKey,
        cardCompany = cardCompany,
        cardLast4 = cardLast4,
        status = status,
        invalidatedAt = invalidatedAt,
        createdAt = createdAt,
    )

    fun updateFrom(billingKey: BillingKey) {
        status = billingKey.status
        invalidatedAt = billingKey.invalidatedAt
    }

    companion object {
        fun fromDomain(billingKey: BillingKey): BillingKeyJpaEntity = BillingKeyJpaEntity(
            customerId = billingKey.customerId,
            customerKey = billingKey.customerKey,
            billingKey = billingKey.billingKey,
            cardCompany = billingKey.cardCompany,
            cardLast4 = billingKey.cardLast4,
            status = billingKey.status,
            invalidatedAt = billingKey.invalidatedAt,
        )
    }
}
