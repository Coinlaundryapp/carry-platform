package org.example.coin_laundry_app_backend.user.presentation.payload.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTTokenResponse;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginResponse {

    private String accessToken;
    private String refreshToken;

    public static LoginResponse from(JWTTokenResponse tokenResponse) {
        return new LoginResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
    }
}
