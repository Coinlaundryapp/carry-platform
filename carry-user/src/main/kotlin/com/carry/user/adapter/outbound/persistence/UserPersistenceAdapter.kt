package com.carry.user.adapter.outbound.persistence

import com.carry.user.adapter.outbound.persistence.entity.UserJpaEntity
import com.carry.user.adapter.outbound.persistence.repository.UserJpaRepository
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
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
        userJpaRepository.findByOauthProviderAndOauthId(oauthInfo.provider, oauthInfo.id)
            .map { it.toDomain() }
            .orElse(null)

    override fun existsByEmail(email: Email): Boolean =
        userJpaRepository.existsByEmail(email.value)
}
