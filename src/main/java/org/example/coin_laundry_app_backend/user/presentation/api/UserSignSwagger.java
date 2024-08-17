package org.example.coin_laundry_app_backend.user.presentation.api;

import static io.netty.handler.codec.http.HttpHeaders.Values.APPLICATION_JSON;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.OAuthCodeRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Tag(name = "User Authentication", description = "사용자 인증 관련 API")
public interface UserSignSwagger {

    @Operation(
        summary = "사용자 로그인",
        description = "OAuth 인증 코드를 사용하여 사용자 로그인을 처리합니다."
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @PostMapping("/login")
    @SecurityRequirement(name = "jwtAuth")
    Mono<ApiCommonResponse<LoginResponse>> login(
        @Parameter(description = "Authentication 코드 요청", required = true) OAuthCodeRequest request
    );

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "토큰 재발급 성공",
        content = @Content(
            mediaType = APPLICATION_JSON,
            schema = @Schema(implementation = LoginResponse.class)
        )
    )
    @PostMapping("/reissue")
    Mono<ApiCommonResponse<LoginResponse>> reissue(
        @CookieValue("refreshToken")
        @Parameter(description = "리프레시 토큰", required = true)
        String refreshToken
    );
}
