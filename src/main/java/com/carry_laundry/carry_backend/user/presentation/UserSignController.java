package com.carry_laundry.carry_backend.user.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.application.service.UserSignService;
import com.carry_laundry.carry_backend.user.presentation.api.UserSignSwagger;
import com.carry_laundry.carry_backend.user.presentation.payload.request.OAuthCodeRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.response.LoginResponse;
import lombok.RequiredArgsConstructor;
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
        return userSignService.loginOrSignUpWithKakao(request.getAuthorizationCode(),
                request.getRedirectUri())
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/reissue")
    public Mono<ApiCommonResponse<LoginResponse>> reissue(
        @CookieValue("refreshToken") String refreshToken) {
        return userSignService.reissue(refreshToken).map(ApiCommonResponse::createSuccessResponse);
    }

}
