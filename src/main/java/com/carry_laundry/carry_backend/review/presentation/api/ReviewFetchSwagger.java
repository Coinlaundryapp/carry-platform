package com.carry_laundry.carry_backend.review.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.common.presentation.payload.CursorPaginationResponse;
import com.carry_laundry.carry_backend.review.presentation.payload.response.ReviewCommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Review", description = "리뷰 관련 API")
public interface ReviewFetchSwagger {

    @Operation(
        summary = "세탁소 리뷰 조회",
        description = "특정 세탁소에 대한 리뷰 목록을 조회합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "성공적으로 조회됨"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 세탁소가 존재하지 않음"
            )
        }
    )
    Mono<ApiCommonResponse<CursorPaginationResponse<ReviewCommonResponse>>> getReviewsByLaundryId(
        @Parameter(in = ParameterIn.PATH, description = "세탁소 ID", example = "1") Long laundromatId,
        @Parameter(in = ParameterIn.QUERY, description = "커서", example = "1") Long cursor,
        @Parameter(in = ParameterIn.QUERY, description = "페이지 크기", example = "10") Integer size
    );
}
