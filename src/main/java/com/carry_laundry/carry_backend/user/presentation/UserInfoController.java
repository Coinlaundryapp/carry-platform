package com.carry_laundry.carry_backend.user.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.application.service.UserTermAgreeService;
import com.carry_laundry.carry_backend.user.presentation.api.UserInfoSwagger;
import com.carry_laundry.carry_backend.user.presentation.payload.request.TermUpdateRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.response.UserTermAgreeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class UserInfoController implements UserInfoSwagger {

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
