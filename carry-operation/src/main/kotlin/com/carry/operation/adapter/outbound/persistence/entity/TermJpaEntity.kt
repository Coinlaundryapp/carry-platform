package com.carry.operation.adapter.outbound.persistence.entity

import com.carry.operation.domain.model.Term
import com.carry.operation.domain.vo.TermType
import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "operation_terms")
class TermJpaEntity(
    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TermType,

    @Column(nullable = false)
    var required: Boolean,

    @Column(nullable = false)
    var version: Int,

    @Column(nullable = false)
    var active: Boolean,
) : BaseEntity() {

    fun toDomain(): Term = Term.reconstitute(
        id = id,
        title = title,
        content = content,
        type = type,
        required = required,
        version = version,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(term: Term) {
        title = term.title
        content = term.content
        required = term.required
        version = term.version
        active = term.active
    }

    companion object {
        fun fromDomain(term: Term): TermJpaEntity = TermJpaEntity(
            title = term.title,
            content = term.content,
            type = term.type,
            required = term.required,
            version = term.version,
            active = term.active,
        )
    }
}
