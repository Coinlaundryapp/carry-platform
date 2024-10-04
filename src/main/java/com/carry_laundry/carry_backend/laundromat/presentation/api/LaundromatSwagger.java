package com.carry_laundry.carry_backend.laundromat.presentation.api;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.laundromat.presentation.payload.response.LaundromatCommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import reactor.core.publisher.Mono;

@Tag(name = "Laundry", description = "세탁소 관련 API")
public interface LaundromatSwagger {

    @Operation(
        summary = "세탁소 위치 조회",
        description = "사용자의 위치를 기반으로 가까운 세탁소 목록을 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<List<LaundromatCommonResponse>>> getLaundromatList(
        @Parameter(description = "사용자의 위도", example = "37.123456", required = true) Double latitude,
        @Parameter(description = "사용자의 경도", example = "127.123456", required = true) Double longitude
    );
}
