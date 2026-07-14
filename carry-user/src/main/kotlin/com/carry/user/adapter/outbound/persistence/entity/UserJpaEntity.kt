package com.carry.user.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user_users")
class UserJpaEntity(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "email_verified", nullable = false)
    val emailVerified: Boolean = false,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var phone: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole = UserRole.CUSTOMER,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseEntity() {

    fun toDomain(): User = User.reconstitute(
        id = id,
        email = Email(email),
        emailVerified = emailVerified,
        name = name,
        phone = Phone(phone),
        role = role,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun updateFrom(user: User) {
        name = user.name
        phone = user.phone.value
        isActive = user.isActive
    }

    companion object {
        fun fromDomain(user: User): UserJpaEntity = UserJpaEntity(
            email = user.email.value,
            emailVerified = user.emailVerified,
            name = user.name,
            phone = user.phone.value,
            role = user.role,
            isActive = user.isActive,
        )
    }
}
