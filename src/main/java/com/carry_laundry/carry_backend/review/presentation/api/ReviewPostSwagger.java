package com.carry_laundry.carry_backend.review.presentation.api;

import com.carry_laundry.carry_backend.review.presentation.payload.request.ReviewCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@Tag(name = "Review", description = "리뷰 관련 API")
public interface ReviewPostSwagger {

    @Operation(
        summary = "리뷰 작성",
        description = "리뷰를 작성합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "201",
                description = "리뷰 작성 성공"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청"
            )
        }
    )
    Mono<ResponseEntity<Void>> postReview(@Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "세탁소 ID", example = "1") Long laundromatId,
        @RequestBody ReviewCreateRequest request);
}
