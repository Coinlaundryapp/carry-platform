package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.UserSignService;
import org.example.coin_laundry_app_backend.user.presentation.api.UserSignSwagger;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.OAuthCodeRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.LoginResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sign")
@RequiredArgsConstructor
public class UserSignController implements UserSignSwagger {

    private final UserSignService userSignService;

    @PostMapping("/login")
    public Mono<ApiCommonResponse<LoginResponse>> login(@RequestBody OAuthCodeRequest request) {
        return userSignService.trySignIn(request.getAuthorizationCode())
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/reissue")
    public Mono<ApiCommonResponse<LoginResponse>> reissue(
        @CookieValue("refreshToken") String refreshToken) {
        return userSignService.reissue(refreshToken).map(ApiCommonResponse::createSuccessResponse);
    }
}
