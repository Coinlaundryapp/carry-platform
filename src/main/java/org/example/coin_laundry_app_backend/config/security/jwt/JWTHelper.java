package org.example.coin_laundry_app_backend.config.security.jwt;

import static com.auth0.jwt.JWT.create;
import static com.auth0.jwt.JWT.require;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class JWTHelper {

    private static final String USER_ID_KEY = "userId";

    private final String issuer;
    private final Long accessTokenExpiryMillis;
    private final Algorithm algorithm;
    private final JWTVerifier jwtVerifier;

    public JWTHelper(JWTProperties jwtProperties) {
        this.issuer = jwtProperties.getIssuer();
        this.accessTokenExpiryMillis = daysToMillis(jwtProperties.getAccessTokenExpiryDate());
        this.algorithm = Algorithm.HMAC256(jwtProperties.getClientSecret());
        this.jwtVerifier = require(algorithm).withIssuer(issuer).build();
    }

    private static Long daysToMillis(Long days) {
        return days * 24 * 60 * 60 * 1000;
    }

    public String sign(Long userId) {
        Date current = new Date();
        return create()
            .withIssuer(issuer)
            .withExpiresAt(calculateExpiryDate(current.getTime()))
            .withClaim(USER_ID_KEY, userId)
            .sign(algorithm);
    }

    public Long verify(String token) {
        var decodedJWT = jwtVerifier.verify(token);
        var claims = decodedJWT.getClaims();
        if (!claims.containsKey(USER_ID_KEY)) {
            throw new JWTVerificationException("Invalid token");
        }
        return claims.get(USER_ID_KEY).asLong();
    }

    private Date calculateExpiryDate(long currentTime) {
        return new Date(currentTime + accessTokenExpiryMillis);
    }
}
