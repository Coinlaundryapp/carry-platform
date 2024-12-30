package com.carry_laundry.carry_backend.term.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.term.presentation.payload.response.TermCodeResponse;
import com.carry_laundry.carry_backend.term.presentation.payload.response.TermCommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Term", description = "약관 API")
public interface TermSwagger {

    @Operation(
        summary = "약관 조회",
        description = "약관 코드로 약관을 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<TermCommonResponse>> getTerm(
        @Parameter(in = ParameterIn.PATH, description = "약관 코드", required = true) String termCode
    );

    @Operation(
        summary = "약관 코드 조회",
        description = "모든 약관 코드를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<TermCodeResponse>> getTermCodes();
}
