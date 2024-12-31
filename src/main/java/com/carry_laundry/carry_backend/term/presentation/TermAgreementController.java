package com.carry_laundry.carry_backend.term.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.term.application.service.TermAgreementService;
import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import com.carry_laundry.carry_backend.term.presentation.api.TermAgreementSwagger;
import com.carry_laundry.carry_backend.term.presentation.payload.request.TermAgreementRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/terms/agreements")
@RequiredArgsConstructor
public class TermAgreementController implements TermAgreementSwagger {

    private final TermAgreementService termAgreementService;

    @PostMapping
    public Mono<ApiCommonResponse<TermAgreement>> createAgreement(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody TermAgreementRequest request
    ) {
        return termAgreementService.createTermAgreement(userId, request.termId(), request.agreeYn())
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PutMapping
    public Mono<ApiCommonResponse<TermAgreement>> updateAgreement(
        @AuthenticationPrincipal Long userId,
        @Valid @RequestBody TermAgreementRequest request
    ) {
        return termAgreementService.updateTermAgreement(userId, request.termId(), request.agreeYn())
            .map(ApiCommonResponse::createSuccessResponse);
    }

}
