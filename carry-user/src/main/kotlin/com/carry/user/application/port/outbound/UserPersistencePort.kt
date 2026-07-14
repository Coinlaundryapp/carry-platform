package com.carry.user.application.port.outbound

import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo

interface UserPersistencePort {

    fun save(user: User): User

    fun findById(id: Long): User?

    fun findByEmail(email: Email): User?

    fun findByOAuthInfo(oauthInfo: OAuthInfo): User?

    fun linkOAuthAccount(userId: Long, oauthInfo: OAuthInfo)

    fun existsByEmail(email: Email): Boolean
}
