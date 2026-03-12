package com.carry_laundry.carry_backend.term.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.term.application.service.TermService;
import com.carry_laundry.carry_backend.term.domain.entity.TermMeta;
import com.carry_laundry.carry_backend.term.presentation.api.TermSwagger;
import com.carry_laundry.carry_backend.term.presentation.payload.response.TermCodeResponse;
import com.carry_laundry.carry_backend.term.presentation.payload.response.TermCommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController implements TermSwagger {

    private final TermService termService;

    @GetMapping("/{termCode}")
    public Mono<ApiCommonResponse<TermCommonResponse>> getTerm(@PathVariable String termCode) {
        return termService.getLastTermByCode(termCode)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping
    public Mono<ApiCommonResponse<TermCodeResponse>> getTermCodes() {
        return termService.getTermMetas()
            .map(TermMeta::getCode)
            .collectList()
            .map(TermCodeResponse::new)
            .map(ApiCommonResponse::createSuccessResponse);
    }

}
