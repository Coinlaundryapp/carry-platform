package com.carry.laundromat.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.laundromat.adapter.inbound.rest.dto.AddMediaResourceRequest
import com.carry.laundromat.adapter.inbound.rest.dto.LaundromatResponse
import com.carry.laundromat.adapter.inbound.rest.dto.NearbyLaundromatResponse
import com.carry.laundromat.adapter.inbound.rest.dto.RegisterLaundromatRequest
import com.carry.laundromat.adapter.inbound.rest.dto.UpdateLaundromatInfoRequest
import com.carry.laundromat.adapter.inbound.rest.dto.UpdateOptionsRequest
import com.carry.laundromat.application.port.inbound.LaundromatCommandUseCase
import com.carry.laundromat.application.port.inbound.LaundromatQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Laundromat", description = "세탁소 관리 API")
@RestController
@RequestMapping("/api/v2/laundromats")
class LaundromatController(
    private val laundromatQueryUseCase: LaundromatQueryUseCase,
    private val laundromatCommandUseCase: LaundromatCommandUseCase,
) {

    companion object {
        /**
         * 세탁소 관리(쓰기) 권한. 세탁소는 운영 카탈로그 엔티티라 운영 역할(COORDINATOR·ADMIN)만 변경할 수 있다.
         * 조회(findNearby·getById)는 고객 탐색용이라 인증만 요구하고 역할 게이트를 두지 않는다.
         */
        const val MANAGE_AUTHORITY = "hasAnyRole('COORDINATOR', 'ADMIN')"
    }

    @Operation(summary = "주변 세탁소 검색", description = "현재 위치 기반으로 반경 내 세탁소를 검색합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "검색 성공")])
    @GetMapping
    fun findNearby(
        @Parameter(description = "위도", example = "37.5665") @RequestParam latitude: Double,
        @Parameter(description = "경도", example = "126.9780") @RequestParam longitude: Double,
        @Parameter(description = "검색 반경(미터)", example = "3000") @RequestParam(defaultValue = "3000") radiusMeters: Int,
    ): ResponseEntity<ApiResponse<List<NearbyLaundromatResponse>>> {
        val result = laundromatQueryUseCase.findNearby(latitude, longitude, radiusMeters)
            .map { NearbyLaundromatResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @Operation(summary = "세탁소 상세 조회")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "세탁소 조회 성공"),
            SwaggerApiResponse(responseCode = "404", description = "세탁소를 찾을 수 없음"),
        ],
    )
    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatQueryUseCase.getById(id)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @Operation(operationId = "registerLaundromat", summary = "세탁소 등록")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "세탁소 등록 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"),
            SwaggerApiResponse(responseCode = "403", description = "운영 역할(COORDINATOR·ADMIN) 권한 없음"),
            SwaggerApiResponse(responseCode = "409", description = "이미 존재하는 세탁소"),
        ],
    )
    @PreAuthorize(MANAGE_AUTHORITY)
    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterLaundromatRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.register(
            name = request.name,
            address = request.toAddress(),
            location = request.toLocation(),
            options = request.options,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(LaundromatResponse.from(laundromat)))
    }

    @Operation(summary = "세탁소 정보 수정")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "세탁소 수정 성공"),
            SwaggerApiResponse(responseCode = "403", description = "운영 역할(COORDINATOR·ADMIN) 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "세탁소를 찾을 수 없음"),
        ],
    )
    @PreAuthorize(MANAGE_AUTHORITY)
    @PutMapping("/{id}")
    fun updateInfo(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateLaundromatInfoRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.updateInfo(
            laundromatId = id,
            name = request.name,
            address = request.toAddress(),
            location = request.toLocation(),
        )
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @Operation(summary = "세탁소 옵션 수정", description = "세탁소의 서비스 옵션을 수정합니다")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "옵션 수정 성공"),
            SwaggerApiResponse(responseCode = "403", description = "운영 역할(COORDINATOR·ADMIN) 권한 없음"),
        ],
    )
    @PreAuthorize(MANAGE_AUTHORITY)
    @PutMapping("/{id}/options")
    fun updateOptions(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateOptionsRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.updateOptions(id, request.options)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }

    @Operation(summary = "세탁소 이미지 추가")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "이미지 추가 성공"),
            SwaggerApiResponse(responseCode = "403", description = "운영 역할(COORDINATOR·ADMIN) 권한 없음"),
        ],
    )
    @PreAuthorize(MANAGE_AUTHORITY)
    @PostMapping("/{id}/media")
    fun addMediaResource(
        @PathVariable id: Long,
        @Valid @RequestBody request: AddMediaResourceRequest,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.addMediaResource(id, request.url, request.extension)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(LaundromatResponse.from(laundromat)))
    }

    @Operation(summary = "세탁소 이미지 삭제")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "이미지 삭제 성공"),
            SwaggerApiResponse(responseCode = "403", description = "운영 역할(COORDINATOR·ADMIN) 권한 없음"),
        ],
    )
    @PreAuthorize(MANAGE_AUTHORITY)
    @DeleteMapping("/{id}/media/{mediaResourceId}")
    fun removeMediaResource(
        @PathVariable id: Long,
        @PathVariable mediaResourceId: Long,
    ): ResponseEntity<ApiResponse<LaundromatResponse>> {
        val laundromat = laundromatCommandUseCase.removeMediaResource(id, mediaResourceId)
        return ResponseEntity.ok(ApiResponse.success(LaundromatResponse.from(laundromat)))
    }
}
