package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.UserInfoService;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.UpdateTermRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.UserTermsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final UserInfoService userInfoService;

    @GetMapping("/terms")
    public Mono<ApiCommonResponse<UserTermsResponse>> getUserTermsInfo(
        @AuthenticationPrincipal Long userId) {
        Mono<UserTermsResponse> userTermsResponseMono = userInfoService.getUserTermsInfo(userId);
        return userTermsResponseMono.map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/terms/commercial")
    public Mono<ApiCommonResponse<Void>> setCommercialTermYn(@AuthenticationPrincipal Long userId,
        @RequestBody UpdateTermRequest request) {
        Mono<Void> voidMono = userInfoService.setCommercialTermYn(userId, request.getAcceptYn());
        return voidMono.then(
            Mono.defer(() -> Mono.just(ApiCommonResponse.createSuccessResponse())));
    }

    @PostMapping("/terms/location")
    public Mono<ApiCommonResponse<Void>> setLocationTermYn(@AuthenticationPrincipal Long userId,
        @RequestBody UpdateTermRequest request) {
        Mono<Void> voidMono = userInfoService.setLocationTermYn(userId, request.getAcceptYn());
        return voidMono.then(
            Mono.defer(() -> Mono.just(ApiCommonResponse.createSuccessResponse())));
    }
}
