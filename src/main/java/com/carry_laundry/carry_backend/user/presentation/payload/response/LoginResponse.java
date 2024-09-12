package com.carry_laundry.carry_backend.user.presentation.payload.response;

import com.carry_laundry.carry_backend.config.security.jwt.JWTTokenResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "로그인 응답")
public class LoginResponse {

    @Schema(description = "액세스 토큰")
    private String accessToken;
    @Schema(description = "리프레시 토큰")
    private String refreshToken;

    public static LoginResponse from(JWTTokenResponse tokenResponse) {
        return new LoginResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
    }
}
