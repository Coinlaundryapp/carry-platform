package com.carry.user.application.service

import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.inbound.Prefill
import com.carry.user.application.port.inbound.TokenPair
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.AuthTokenInvalidException
import com.carry.user.domain.exception.InactiveUserException
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
    private val oAuthProfileClient: OAuthProfileClient,
    private val authTokenPort: AuthTokenPort,
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

    @Transactional(readOnly = true)
    override fun loginWithKakao(kakaoAccessToken: String): LoginResult {
        val profile = oAuthProfileClient.fetchKakaoProfile(kakaoAccessToken)
        val existing = userPersistencePort.findByOAuthInfo(OAuthInfo(OAuthProvider.KAKAO, profile.oauthId))

        return if (existing != null) {
            if (!existing.isActive) throw InactiveUserException()
            LoginResult.Registered(issueTokens(existing))
        } else {
            val signupToken = authTokenPort.issueSignupToken(
                OAuthProvider.KAKAO, profile.oauthId, profile.email, profile.nickname,
            )
            LoginResult.RegistrationRequired(signupToken, Prefill(profile.email, profile.nickname))
        }
    }

    override fun completeSignup(signupToken: String, name: String, phone: String, email: String): TokenPair {
        val identity = authTokenPort.parseSignupToken(signupToken) ?: throw AuthTokenInvalidException()
        val user = loginOrRegister(identity.provider, identity.oauthId, email, name, phone)
        if (!user.isActive) throw InactiveUserException()
        return issueTokens(user)
    }

    @Transactional(readOnly = true)
    override fun refresh(refreshToken: String): String {
        val userId = authTokenPort.parseRefreshToken(refreshToken) ?: throw AuthTokenInvalidException()
        val user = userPersistencePort.findById(userId) ?: throw AuthTokenInvalidException()
        if (!user.isActive) throw AuthTokenInvalidException()
        return authTokenPort.issueAccessToken(user.id!!, user.role)
    }

    private fun issueTokens(user: User): TokenPair = TokenPair(
        accessToken = authTokenPort.issueAccessToken(user.id!!, user.role),
        refreshToken = authTokenPort.issueRefreshToken(user.id!!),
    )
}
