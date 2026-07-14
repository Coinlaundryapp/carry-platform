package com.carry.user.adapter.outbound.persistence

import com.carry.user.adapter.outbound.persistence.entity.UserJpaEntity
import com.carry.user.adapter.outbound.persistence.entity.UserOAuthAccountJpaEntity
import com.carry.user.adapter.outbound.persistence.repository.UserJpaRepository
import com.carry.user.adapter.outbound.persistence.repository.UserOAuthAccountJpaRepository
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val userOAuthAccountJpaRepository: UserOAuthAccountJpaRepository,
) : UserPersistencePort {

    override fun save(user: User): User {
        val entity = if (user.id != null) {
            val existing = userJpaRepository.getReferenceById(user.id)
            existing.updateFrom(user)
            existing
        } else {
            UserJpaEntity.fromDomain(user)
        }
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): User? =
        userJpaRepository.findById(id)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByEmail(email: Email): User? =
        userJpaRepository.findByEmail(email.value)
            .map { it.toDomain() }
            .orElse(null)

    override fun findByOAuthInfo(oauthInfo: OAuthInfo): User? =
        userOAuthAccountJpaRepository.findByProviderAndOauthId(oauthInfo.provider, oauthInfo.id)
            .flatMap { userJpaRepository.findById(it.userId) }
            .map { it.toDomain() }
            .orElse(null)

    override fun linkOAuthAccount(userId: Long, oauthInfo: OAuthInfo) {
        userOAuthAccountJpaRepository.save(
            UserOAuthAccountJpaEntity(
                userId = userId,
                provider = oauthInfo.provider,
                oauthId = oauthInfo.id,
            ),
        )
    }

    override fun existsByEmail(email: Email): Boolean =
        userJpaRepository.existsByEmail(email.value)
}
