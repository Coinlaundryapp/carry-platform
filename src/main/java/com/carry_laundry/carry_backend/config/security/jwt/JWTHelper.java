package com.carry_laundry.carry_backend.config.security.jwt;

import static com.auth0.jwt.JWT.create;
import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class JWTHelper {

    public static final String USER_ID_KEY = "USER_ID";
    public static final String TERMS_KEY = "TERMS";

    private final String issuer;
    private final Long accessTokenExpiryMillis;
    private final Long refreshTokenExpiryMillis;
    private final Algorithm algorithm;
    private final JWTVerifier jwtVerifier;

    public JWTHelper(JWTProperties jwtProperties) {
        this.issuer = jwtProperties.getIssuer();
        this.accessTokenExpiryMillis = daysToMillis(jwtProperties.getAccessTokenExpiryDate());
        this.refreshTokenExpiryMillis = daysToMillis(jwtProperties.getRefreshTokenExpiryDate());
        this.algorithm = Algorithm.HMAC256(jwtProperties.getClientSecret());
        this.jwtVerifier = require(algorithm).withIssuer(issuer).build();
    }

    public JWTTokenResponse sign(Long userId, Map<String, Long> acceptedTerms) {
        Date current = new Date();
        String accessToken = generateAccessToken(userId, acceptedTerms, current);
        Date refreshTokenExpiryDate = calculateExpiryDate(current.getTime(),
            refreshTokenExpiryMillis);
        String refreshToken = create()
            .withIssuer(issuer)
            .withExpiresAt(refreshTokenExpiryDate)
            .sign(algorithm);
        return JWTTokenResponse.of(accessToken, refreshToken, refreshTokenExpiryDate);
    }

    public JWTTokenResponse sign(Long userId, Map<String, Long> acceptedTerms, String refreshToken,
        LocalDateTime refreshTokenExpiryDate) {
        Date current = new Date();
        String accessToken = generateAccessToken(userId, acceptedTerms, current);
        return JWTTokenResponse.of(accessToken, refreshToken, refreshTokenExpiryDate);
    }

    public TokenDetail parse(String token) {
        DecodedJWT decodedJWT = jwtVerifier.verify(token);
        Map<String, Claim> claims = decodedJWT.getClaims();
        long userId = Optional.ofNullable(claims.get(USER_ID_KEY))
            .orElseThrow(() -> new JWTVerificationException("Invalid Token")).asLong();
        Map<String, Long> acceptedTerms = parseAcceptedTerms(claims.get(TERMS_KEY));
        return new TokenDetail(userId, acceptedTerms);
    }

    private String generateAccessToken(Long userId, Map<String, Long> acceptedTerm,
        Date currentDate) {
        return create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(currentDate.getTime(), accessTokenExpiryMillis))
            .withClaim(USER_ID_KEY, userId)
            .withClaim(TERMS_KEY, acceptedTerm)
            .sign(algorithm);
    }

    private Map<String, Long> parseAcceptedTerms(Claim claim) {
        return claim.asMap().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> ((Number) e.getValue()).longValue()));
    }

    private Long daysToMillis(@NonNull Long days) {
        return days * 24 * 60 * 60 * 1000;
    }

    private Date calculateExpiryDate(long currentTime, long expiryTime) {
        return new Date(currentTime + expiryTime);
    }
}
