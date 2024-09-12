package com.carry_laundry.carry_backend.user.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.presentation.payload.request.TermUpdateRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.response.UserTermAgreeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import reactor.core.publisher.Mono;

@Tag(name = "User Terms", description = "사용자 약관 동의 관련 API")
@SecurityRequirement(name = "jwtAuth")
public interface UserInfoSwagger {

    @Operation(
        summary = "사용자 약관 동의 정보 조회",
        description = "현재 로그인한 사용자의 모든 약관 동의 정보를 조회합니다. 각 약관에 대한 동의 여부와 동의 일시를 포함합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<List<UserTermAgreeResponse>>> getUserTermsInfo(
        @Parameter(hidden = true) Long userId);

    @Operation(
        summary = "약관 동의",
        description = "특정 약관에 대해 사용자의 동의를 처리합니다. 동의 시 현재 시간이 동의 일시로 기록됩니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 동의 처리됨"
    )
    Mono<ApiCommonResponse<UserTermAgreeResponse>> requestAgreeTerm(
        @Parameter(hidden = true) Long userId,
        @Parameter(description = "동의할 약관 정보", required = true) TermUpdateRequest request);

    @Operation(
        summary = "약관 철회",
        description = "특정 약관에 대해 사용자를 철회 처리합니다. 이전에 동의한 약관에 대해서도 철회 처리가 가능합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 철회 처리됨"
    )
    Mono<ApiCommonResponse<UserTermAgreeResponse>> requestDisagreeTerm(
        @Parameter(hidden = true) Long userId,
        @Parameter(description = "철회할 약관 정보", required = true) TermUpdateRequest request);
}
