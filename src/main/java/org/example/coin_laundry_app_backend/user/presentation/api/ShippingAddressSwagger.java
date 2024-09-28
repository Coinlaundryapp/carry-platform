package org.example.coin_laundry_app_backend.user.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.record.shippingaddress.ShippingAddressSummary;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.UpdateAddressRequest;
import reactor.core.publisher.Mono;

@Tag(name = "Shipping Address", description = "배송지 관리 API")
@SecurityRequirement(name = "jwtAuth")
public interface ShippingAddressSwagger {

    @Operation(
        summary = "기본 배송지 조회",
        description = "현재 로그인한 사용자의 기본 배송지 정보를 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<ShippingAddress>> getDefaultShippingAddress(
        @Parameter(hidden = true) Long userId);

    @Operation(
        summary = "배송지 목록 조회",
        description = "현재 로그인한 사용자의 모든 배송지 목록을 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "성공적으로 조회됨"
    )
    Mono<ApiCommonResponse<List<ShippingAddressSummary>>> getAllShippingAddresses(
        @Parameter(hidden = true) Long userId);

    @Operation(
        summary = "배송지 조회",
        description = "특정 배송지 ID에 해당하는 배송지 정보를 조회합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "성공적으로 조회됨"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 ID에 해당하는 배송지가 존재하지 않음"
            )
        }
    )
    Mono<ApiCommonResponse<ShippingAddress>> getShippingAddressById(
        @Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "배송지 ID", example = "1") Long shippingAddressId);

    @Operation(
        summary = "배송지 추가",
        description = "새로운 배송지를 추가합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "성공적으로 추가됨"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "입력값이 잘못되었음"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "권한이 없음"
            )
        }
    )
    Mono<ShippingAddress> addShippingAddress(
        @Parameter(hidden = true) Long userId,
        @Parameter(description = "추가할 배송지 정보", required = true) CreateAddressRequest request);


    @Operation(
        summary = "배송지 수정",
        description = "기존 배송지 정보를 수정합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "성공적으로 수정됨"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "입력값이 잘못되었음"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "권한이 없음"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 ID에 해당하는 배송지가 존재하지 않음"
            )
        }
    )
    Mono<ShippingAddress> updateShippingAddress(
        @Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "배송지 ID", example = "1") Long shippingAddressId,
        @Parameter(description = "수정할 배송지 정보", required = true) UpdateAddressRequest request);

    @Operation(
        summary = "배송지 삭제",
        description = "특정 배송지 ID에 해당하는 배송지 정보를 삭제합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "204",
                description = "성공적으로 삭제됨"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "권한이 없음"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 ID에 해당하는 배송지가 존재하지 않음"
            )
        }
    )
    Mono<Void> deleteShippingAddress(
        @Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "배송지 ID", example = "1") Long shippingAddressId
    );

    @Operation(
        summary = "기본 배송지 설정",
        description = "특정 배송지 ID에 해당하는 배송지를 기본 배송지로 설정합니다."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "성공적으로 설정됨"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "권한이 없음"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "해당 ID에 해당하는 배송지가 존재하지 않음"
            )
        }
    )
    Mono<ApiCommonResponse<Void>> setDefaultShippingAddress(
        @Parameter(hidden = true) Long userId,
        @Parameter(in = ParameterIn.PATH, description = "배송지 ID", example = "1") Long shippingAddressId
    );
}
