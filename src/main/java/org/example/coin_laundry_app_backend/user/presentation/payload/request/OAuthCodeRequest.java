package org.example.coin_laundry_app_backend.user.presentation.payload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "OAuth 인증 코드 요청")
public class OAuthCodeRequest {

    @Schema(description = "OAuth 인증 코드", example = "123456")
    private String authorizationCode;
}
