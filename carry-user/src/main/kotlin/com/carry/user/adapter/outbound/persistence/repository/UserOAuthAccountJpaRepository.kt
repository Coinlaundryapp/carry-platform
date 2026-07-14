package com.carry.user.adapter.outbound.persistence.repository

import com.carry.user.adapter.outbound.persistence.entity.UserOAuthAccountJpaEntity
import com.carry.user.domain.vo.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserOAuthAccountJpaRepository : JpaRepository<UserOAuthAccountJpaEntity, Long> {

    fun findByProviderAndOauthId(
        provider: OAuthProvider,
        oauthId: String,
    ): Optional<UserOAuthAccountJpaEntity>
}
