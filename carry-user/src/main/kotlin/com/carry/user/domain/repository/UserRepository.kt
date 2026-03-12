package com.carry.user.domain.repository

import com.carry.user.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findByOauthProviderAndOauthId(oauthProvider: com.carry.user.domain.model.OAuthProvider, oauthId: String): Optional<User>
    fun existsByEmail(email: String): Boolean
}
