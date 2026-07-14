package com.carry.user.adapter.outbound.persistence.entity

import com.carry.infra.persistence.BaseEntity
import com.carry.user.domain.vo.OAuthProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "user_oauth_accounts")
class UserOAuthAccountJpaEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: OAuthProvider,

    @Column(name = "oauth_id", nullable = false)
    val oauthId: String,
) : BaseEntity()
