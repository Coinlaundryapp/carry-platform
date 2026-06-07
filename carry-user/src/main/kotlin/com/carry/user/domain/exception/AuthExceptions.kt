package com.carry.user.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

/** signup/refresh 토큰이 무효·만료이거나 purpose가 맞지 않음. */
class AuthTokenInvalidException :
    BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "유효하지 않은 인증 토큰입니다")

/** OAuth 제공자(Kakao) access token이 무효·만료. */
class OAuthTokenInvalidException :
    BusinessException(ErrorCode.OAUTH_TOKEN_INVALID, "유효하지 않은 소셜 로그인 토큰입니다")

/** OAuth 제공자(Kakao) 호출 실패(장애·타임아웃). */
class OAuthProviderUnavailableException(cause: Throwable? = null) :
    BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE, "소셜 로그인 제공자에 일시적으로 접속할 수 없습니다", cause)
