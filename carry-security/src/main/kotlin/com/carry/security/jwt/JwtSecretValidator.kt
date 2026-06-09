package com.carry.security.jwt

import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

/**
 * 애플리케이션 기동 시 `jwt.secret` 강도를 검증해 fail-fast 한다.
 *
 * 서명 알고리즘은 HS256(HMAC-SHA256)이며 256-bit(=32 byte) 이상의 키가 필요하다. auth0 JWT 의
 * `Algorithm.HMAC256` 은 jjwt 와 달리 약한 키를 거부하지 않고, [JwtProvider] 의 algorithm 은
 * lazy 초기화라 기동 시 검증되지 않는다 → 짧거나 비어 있는 비밀키가 조용히 통과해 토큰 위조 위험.
 *
 * 본 검증으로 미설정·약한 `JWT_SECRET` 이면 기동을 즉시 실패시켜 운영 사고를 예방한다.
 */
@Component
class JwtSecretValidator(
    private val jwtProperties: JwtProperties,
) : InitializingBean {

    override fun afterPropertiesSet() {
        val bytes = jwtProperties.secret.toByteArray(Charsets.UTF_8).size
        check(bytes >= MIN_SECRET_BYTES) {
            "jwt.secret 이 너무 짧습니다($bytes bytes). HS256 서명에는 최소 $MIN_SECRET_BYTES bytes(256-bit) 이상이 " +
                "필요합니다. JWT_SECRET 환경변수를 32자 이상의 강한 임의 문자열로 설정하세요."
        }
    }

    companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
