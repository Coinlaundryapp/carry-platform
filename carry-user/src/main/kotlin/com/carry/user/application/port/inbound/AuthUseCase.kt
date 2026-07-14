package com.carry.user.application.port.inbound

import com.carry.user.domain.model.User
import com.carry.user.domain.vo.OAuthProvider
import com.carry.user.domain.vo.UserRole

interface AuthUseCase {

    /**
     * 비프로덕션 dev-login — 주어진 역할의 결정적 dev 사용자를 get-or-create하고 토큰을 발급한다.
     * Kakao 콘솔 의존 없이 로컬/개발에서 인증을 도달 가능하게 한다. prod에서는 노출 경로가 봉인된다.
     */
    fun devLogin(role: UserRole): TokenPair

    fun loginOrRegister(
        provider: OAuthProvider,
        oauthId: String,
        email: String,
        emailVerified: Boolean,
        name: String,
        phone: String,
    ): User

    /** Kakao access token을 검증해 로그인. 기존 유저면 토큰, 신규면 가입 단계 토큰을 반환. */
    fun loginWithKakao(kakaoAccessToken: String): LoginResult

    /** 가입 토큰(Kakao 신원) + 폼(name/phone/email)으로 회원가입을 완료하고 토큰을 발급. */
    fun completeSignup(signupToken: String, name: String, phone: String, email: String): TokenPair

    /** refresh 토큰을 회전한다 — 새 access + 새 refresh를 발급하고 이전 refresh를 무효화. */
    fun refresh(refreshToken: String): TokenPair

    /** 로그아웃 — refresh 토큰의 세션을 폐기한다. 멱등. */
    fun logout(refreshToken: String)
}
