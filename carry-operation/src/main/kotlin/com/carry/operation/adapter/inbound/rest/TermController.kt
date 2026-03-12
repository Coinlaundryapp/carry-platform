package com.carry.operation.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.operation.adapter.inbound.rest.dto.TermResponse
import com.carry.operation.application.port.inbound.TermQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Terms", description = "이용약관 API")
@RestController
@RequestMapping("/api/v2/terms")
class TermController(
    private val termQueryUseCase: TermQueryUseCase,
) {

    @Operation(summary = "활성 약관 목록 조회", description = "현재 활성화된 모든 이용약관을 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "약관 목록 조회 성공")])
    @GetMapping
    fun getActiveTerms(): ResponseEntity<ApiResponse<List<TermResponse>>> {
        val terms = termQueryUseCase.getActiveTerms()
        return ResponseEntity.ok(ApiResponse.success(terms.map { TermResponse.from(it) }))
    }

    @Operation(summary = "필수 약관 목록 조회", description = "동의가 필수인 약관만 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "필수 약관 목록 조회 성공")])
    @GetMapping("/required")
    fun getRequiredTerms(): ResponseEntity<ApiResponse<List<TermResponse>>> {
        val terms = termQueryUseCase.getRequiredTerms()
        return ResponseEntity.ok(ApiResponse.success(terms.map { TermResponse.from(it) }))
    }
}
