package com.carry.user.application.service

import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userPersistencePort: UserPersistencePort,
) : AuthUseCase {

    override fun loginOrRegister(
        provider: OAuthProvider,
        oauthId: String,
        email: String,
        name: String,
        phone: String,
    ): User {
        val oauthInfo = OAuthInfo(provider, oauthId)

        return userPersistencePort.findByOAuthInfo(oauthInfo)
            ?: userPersistencePort.save(
                User.create(
                    email = Email(email),
                    name = name,
                    phone = Phone(phone),
                    oauthInfo = oauthInfo,
                ),
            )
    }
}
