package org.example.coin_laundry_app_backend.user.presentation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.UserTermAgreeService;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.TermUpdateRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.response.UserTermAgreeResponse;
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

    private final UserTermAgreeService userTermAgreeService;

    @GetMapping("/terms")
    public Mono<ApiCommonResponse<List<UserTermAgreeResponse>>> getUserTermsInfo(
        @AuthenticationPrincipal Long userId) {
        return userTermAgreeService.getTermAgrees(userId).collectList()
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/terms/agree")
    public Mono<ApiCommonResponse<UserTermAgreeResponse>> requestAgreeTerm(
        @AuthenticationPrincipal Long userId, @RequestBody TermUpdateRequest request) {
        return userTermAgreeService.agreeTerm(userId, request.getTermId())
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/terms/disagree")
    public Mono<ApiCommonResponse<UserTermAgreeResponse>> requestDisagreeTerm(
        @AuthenticationPrincipal Long userId, @RequestBody TermUpdateRequest request) {
        return userTermAgreeService.disagreeTerm(userId, request.getTermId())
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
