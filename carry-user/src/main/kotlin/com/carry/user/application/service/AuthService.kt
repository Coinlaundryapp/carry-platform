package com.carry.user.application.service

import com.carry.user.application.port.inbound.AuthUseCase
import com.carry.user.application.port.inbound.LoginResult
import com.carry.user.application.port.inbound.Prefill
import com.carry.user.application.port.inbound.TokenPair
import com.carry.user.application.port.outbound.AuthTokenPort
import com.carry.user.application.port.outbound.OAuthProfileClient
import com.carry.user.application.port.outbound.RefreshTokenStorePort
import com.carry.user.application.port.outbound.RotateResult
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.AuthTokenInvalidException
import com.carry.user.domain.exception.InactiveUserException
import com.carry.user.domain.exception.RefreshTokenReuseException
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.OAuthInfo
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.Phone
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userPersistencePort: UserPersistencePort,
    private val oAuthProfileClient: OAuthProfileClient,
    private val authTokenPort: AuthTokenPort,
    private val refreshTokenStorePort: RefreshTokenStorePort,
) : AuthUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

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

    /**
     * refresh 토큰 회전. ⚠️ readOnly 아님 — Redis allowlist 상태(회전/폐기)를 변경한다.
     * Redis 연산은 JPA 트랜잭션 밖이라 롤백되지 않으므로 DB 조회 후 회전 순서를 유지한다.
     */
    override fun refresh(refreshToken: String): TokenPair {
        val claims = authTokenPort.parseRefreshToken(refreshToken) ?: throw AuthTokenInvalidException()
        val user = userPersistencePort.findById(claims.userId) ?: throw AuthTokenInvalidException()
        if (!user.isActive) throw AuthTokenInvalidException()

        val rotated = authTokenPort.issueRefreshToken(user.id!!, claims.sessionId)
        return when (refreshTokenStorePort.rotate(claims.sessionId, claims.jti, rotated.jti)) {
            RotateResult.ROTATED -> TokenPair(
                accessToken = authTokenPort.issueAccessToken(user.id!!, user.role),
                refreshToken = rotated.token,
            )
            RotateResult.ABSENT -> throw AuthTokenInvalidException()
            RotateResult.REUSE -> {
                log.warn("refresh 토큰 재사용 감지 — 세션 폐기 (userId={}, sessionId={})", claims.userId, claims.sessionId)
                throw RefreshTokenReuseException()
            }
        }
    }

    override fun logout(refreshToken: String) {
        // 완전 검증된 토큰에서만 sessionId를 신뢰 — 미검증 토큰으로 임의 세션 evict 차단.
        val claims = authTokenPort.parseRefreshToken(refreshToken) ?: return
        refreshTokenStorePort.delete(claims.sessionId)
    }

    private fun issueTokens(user: User): TokenPair {
        val issued = authTokenPort.issueRefreshToken(user.id!!)
        refreshTokenStorePort.start(issued.sessionId, issued.jti)
        return TokenPair(
            accessToken = authTokenPort.issueAccessToken(user.id!!, user.role),
            refreshToken = issued.token,
        )
    }
}
