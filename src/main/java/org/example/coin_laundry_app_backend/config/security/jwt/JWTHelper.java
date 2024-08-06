package org.example.coin_laundry_app_backend.config.security.jwt;

import static com.auth0.jwt.JWT.create;
import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.coin_laundry_app_backend.user.application.service.TermAdminService;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.Term;
import org.springframework.stereotype.Component;

@Component
public class JWTHelper {

    private static final String USER_ID_KEY = "userId";
    private static final String TERMS_KEY = "terms";

    private final String issuer;
    private final Long accessTokenExpiryMillis;
    private final Algorithm algorithm;
    private final JWTVerifier jwtVerifier;
    private final TermAdminService termAdminService;

    public JWTHelper(JWTProperties jwtProperties, TermAdminService termAdminService) {
        this.issuer = jwtProperties.getIssuer();
        this.accessTokenExpiryMillis = daysToMillis(jwtProperties.getAccessTokenExpiryDate());
        this.algorithm = Algorithm.HMAC256(jwtProperties.getClientSecret());
        this.jwtVerifier = require(algorithm).withIssuer(issuer).build();
        this.termAdminService = termAdminService;
    }

    @Deprecated(forRemoval = true)
    public String sign(Long userId) {
        Date current = new Date();
        return create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(current.getTime()))
            .withClaim(USER_ID_KEY, userId)
            .sign(algorithm);
    }

    public String sign(Long userId, List<Term> acceptedTerms) {
        Date current = new Date();
        return create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(current.getTime()))
            .withClaim(USER_ID_KEY, userId)
            .withArrayClaim(TERMS_KEY, acceptedTerms.stream().map(Term::getId).toArray(Long[]::new))
            .sign(algorithm);
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

    private Date calculateExpiryDate(long currentTime) {
        return new Date(currentTime + accessTokenExpiryMillis);
    }
}
