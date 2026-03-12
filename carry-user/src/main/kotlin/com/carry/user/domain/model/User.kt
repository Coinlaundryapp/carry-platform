package com.carry.user.domain.model

import com.carry.infra.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user_users")
class User(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var phone: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole = UserRole.CUSTOMER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val oauthProvider: OAuthProvider,

    @Column(nullable = false)
    val oauthId: String,

    @Column(nullable = false)
    var isActive: Boolean = true
) : BaseEntity() {

    fun updateProfile(name: String, phone: String) {
        this.name = name
        this.phone = phone
    }

    fun deactivate() {
        this.isActive = false
    }
}

enum class UserRole {
    CUSTOMER, RIDER, OWNER, ADMIN
}

enum class OAuthProvider {
    KAKAO
}
