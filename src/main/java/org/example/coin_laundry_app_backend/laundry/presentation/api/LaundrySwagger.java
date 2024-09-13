package org.example.coin_laundry_app_backend.laundry.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.laundry.presentation.payload.request.LaundryLocationRequest;
import org.example.coin_laundry_app_backend.laundry.presentation.payload.response.LaundryCommonResponse;
import reactor.core.publisher.Mono;

@Tag(name = "Laundry", description = "세탁소 관련 API")
public interface LaundrySwagger {

    @Operation(
        summary = "세탁소 위치 조회",
        description = "사용자의 위치를 기반으로 가까운 세탁소 목록을 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<List<LaundryCommonResponse>>> getLaundryList(
        @Parameter(description = "위치 정보", required = true) LaundryLocationRequest request);
}
