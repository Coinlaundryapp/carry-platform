package com.carry.user.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

/** signup/refresh 토큰이 무효·만료이거나 purpose가 맞지 않음. */
class AuthTokenInvalidException :
    BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "유효하지 않은 인증 토큰입니다")

/**
 * 이미 회전된 옛 refresh 토큰의 재사용(탈취 신호)으로 세션이 폐기됨.
 * 클라이언트 응답은 [AuthTokenInvalidException]과 동일한 401 봉투지만, 별도 타입으로
 * warn 로깅·향후 감사 로그 연결의 식별자가 된다.
 */
class RefreshTokenReuseException :
    BusinessException(ErrorCode.AUTH_TOKEN_INVALID, "재사용이 감지되어 세션이 폐기되었습니다. 다시 로그인하세요")

/** OAuth 제공자(Kakao) access token이 무효·만료. */
class OAuthTokenInvalidException :
    BusinessException(ErrorCode.OAUTH_TOKEN_INVALID, "유효하지 않은 소셜 로그인 토큰입니다")

/** OAuth 제공자(Kakao) 호출 실패(장애·타임아웃). */
class OAuthProviderUnavailableException(cause: Throwable? = null) :
    BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE, "소셜 로그인 제공자에 일시적으로 접속할 수 없습니다", cause)
