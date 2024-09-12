package com.carry_laundry.carry_backend.config.security.jwt;

import static com.auth0.jwt.JWT.create;
import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.carry_laundry.carry_backend.user.application.service.TermAdminService;
import com.carry_laundry.carry_backend.user.domain.model.entity.domainmodel.Term;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JWTHelper {

    private static final String USER_ID_KEY = "userId";
    private static final String TERMS_KEY = "terms";

    private final String issuer;
    private final Long accessTokenExpiryMillis;
    private final Long refreshTokenExpiryMillis;
    private final Algorithm algorithm;
    private final JWTVerifier jwtVerifier;
    private final TermAdminService termAdminService;

    public JWTHelper(JWTProperties jwtProperties, TermAdminService termAdminService) {
        this.issuer = jwtProperties.getIssuer();
        this.accessTokenExpiryMillis = daysToMillis(jwtProperties.getAccessTokenExpiryDate());
        this.refreshTokenExpiryMillis = daysToMillis(jwtProperties.getRefreshTokenExpiryDate());
        this.algorithm = Algorithm.HMAC256(jwtProperties.getClientSecret());
        this.jwtVerifier = require(algorithm).withIssuer(issuer).build();
        this.termAdminService = termAdminService;
    }

    public JWTTokenResponse sign(Long userId, List<Long> acceptedTerms) {
        Date current = new Date();
        String accessToken = create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(current.getTime(), accessTokenExpiryMillis))
            .withClaim(USER_ID_KEY, userId)
            .withArrayClaim(TERMS_KEY, acceptedTerms.toArray(new Long[0]))
            .sign(algorithm);
        Date refreshTokenExpiryDate = calculateExpiryDate(current.getTime(),
            refreshTokenExpiryMillis);
        String refreshToken = create()
            .withIssuer(issuer)
            .withExpiresAt(refreshTokenExpiryDate)
            .sign(algorithm);
        return JWTTokenResponse.of(accessToken, refreshToken, refreshTokenExpiryDate);
    }

    public JWTTokenResponse sign(Long userId, List<Long> acceptedTerms, String refreshToken,
        LocalDateTime refreshTokenExpiryDate) {
        Date current = new Date();
        String accessToken = create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(current.getTime(), accessTokenExpiryMillis))
            .withClaim(USER_ID_KEY, userId)
            .withArrayClaim(TERMS_KEY, acceptedTerms.toArray(new Long[0]))
            .sign(algorithm);
        return JWTTokenResponse.of(accessToken, refreshToken, refreshTokenExpiryDate);
    }

    public Long verify(String token) {
        DecodedJWT decodedJWT = jwtVerifier.verify(token);
        Map<String, Claim> claims = decodedJWT.getClaims();
        Long[] requiredTerms = termAdminService.getRequiredTerms().stream().map(Term::getId)
            .sorted()
            .toArray(Long[]::new);
        Long[] acceptedTerms = claims.get(TERMS_KEY).asArray(Long.class);
        Arrays.sort(acceptedTerms);
        if (Arrays.compare(requiredTerms, acceptedTerms) != 0) {
            throw new JWTVerificationException("약관 동의가 필요합니다.");
        }
        return Optional.ofNullable(claims.get(USER_ID_KEY))
            .orElseThrow(() -> new JWTVerificationException("Invalid Token")).asLong();
    }

    private Long daysToMillis(Long days) {
        return days * 24 * 60 * 60 * 1000;
    }

    private Date calculateExpiryDate(long currentTime, long expiryTime) {
        return new Date(currentTime + expiryTime);
    }
}
