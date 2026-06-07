package com.carry.user.application.port.inbound

import com.carry.user.domain.model.User
import com.carry.user.domain.vo.OAuthProvider

interface AuthUseCase {

    fun loginOrRegister(
        provider: OAuthProvider,
        oauthId: String,
        email: String,
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
