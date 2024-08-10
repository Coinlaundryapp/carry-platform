package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.UserSignService;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.OAuthTokenRequest;
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
public class UserSignController {

    private final UserSignService userSignService;


    @PostMapping("/login")
    public Mono<ApiCommonResponse<LoginResponse>> login(@RequestBody OAuthTokenRequest request) {
        return userSignService.login(request.getAccessToken())
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/sign-up")
    public Mono<ApiCommonResponse<Long>> signUp(@RequestBody OAuthTokenRequest request) {
        return userSignService.signUp(request.getAccessToken())
            .map(user -> ApiCommonResponse.createSuccessResponse(user.getId()));
    }

    @PostMapping("/reissue")
    public Mono<ApiCommonResponse<LoginResponse>> reissue(
        @CookieValue("refreshToken") String refreshToken) {
        return userSignService.reissue(refreshToken).map(ApiCommonResponse::createSuccessResponse);
    }

}
