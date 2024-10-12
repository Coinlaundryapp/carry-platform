package com.carry_laundry.carry_backend.review.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Review", description = "리뷰 관련 API")
public interface ReviewManageSwagger {

    @Operation(
        summary = "리뷰 삭제",
        description = "리뷰를 삭제합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "리뷰 삭제 성공"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "권한 없음"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 리뷰가 존재하지 않음"
            )
        }
    )
    Mono<ApiCommonResponse<Void>> deleteReview(
        @Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "리뷰 ID", example = "1") Long reviewId);
}
