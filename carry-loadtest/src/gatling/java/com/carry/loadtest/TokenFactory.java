package com.carry.loadtest;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.Date;

/** 서버와 동일한 secret/클레임으로 access 토큰을 생성한다(JwtProvider.createAccessToken 미러). */
public final class TokenFactory {
    private static final String SECRET =
        System.getenv().getOrDefault("JWT_SECRET", "test-secret-key-for-integration-tests");

    public static String accessToken(long userId, String role) {
        Algorithm alg = Algorithm.HMAC256(SECRET);
        return JWT.create()
            .withSubject(String.valueOf(userId))
            .withClaim("purpose", "ACCESS")
            .withClaim("role", role)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
            .sign(alg);
    }
}
