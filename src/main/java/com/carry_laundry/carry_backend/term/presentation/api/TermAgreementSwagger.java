package com.carry_laundry.carry_backend.term.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.term.domain.entity.TermAgreement;
import com.carry_laundry.carry_backend.term.presentation.payload.request.TermAgreementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Term Agreements", description = "약관 동의 관련 API")
@SecurityRequirement(name = "jwtAuth")
public interface TermAgreementSwagger {

    @Operation(
        summary = "약관 동의 생성",
        description = "사용자의 약관 동의/비동의를 생성합니다. 이미 동의한 약관에 대해서는 중복 생성이 불가능합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 생성됨"
    )
    Mono<ApiCommonResponse<TermAgreement>> createAgreement(
        @Parameter(hidden = true) Long userId,
        @Parameter(description = "약관 동의/비동의 생성 요청", required = true) TermAgreementRequest request
    );

    @Operation(
        summary = "약관 동의 업데이트",
        description = "사용자의 약관 동의/비동의를 업데이트합니다. 이미 동의한 약관에 대해서는 중복 업데이트가 불가능합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 업데이트됨"
    )
    Mono<ApiCommonResponse<TermAgreement>> updateAgreement(
        @Parameter(hidden = true) Long userId,
        @Parameter(description = "약관 동의/비동의 업데이트 요청", required = true) TermAgreementRequest request
    );
}
