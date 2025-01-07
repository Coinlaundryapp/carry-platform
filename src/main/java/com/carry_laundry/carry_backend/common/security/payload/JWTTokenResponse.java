package com.carry_laundry.carry_backend.common.security.payload;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import lombok.Getter;

@Getter
public class JWTTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final LocalDateTime refreshTokenExpiryAt;

    private JWTTokenResponse(String accessToken, String refreshToken,
        LocalDateTime refreshTokenExpiryAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiryAt = refreshTokenExpiryAt;
    }

    public static JWTTokenResponse of(String accessToken, String refreshToken,
        Date refreshtokenExpiryDate) {
        LocalDateTime expiryAt = refreshtokenExpiryDate.toInstant().atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        return new JWTTokenResponse(accessToken, refreshToken, expiryAt);
    }

    public static JWTTokenResponse of(String accessToken, String refreshToken,
        LocalDateTime refreshTokenExpiryAt) {
        return new JWTTokenResponse(accessToken, refreshToken, refreshTokenExpiryAt);
    }
}
