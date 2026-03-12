package com.carry.operation.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.operation.adapter.inbound.rest.dto.CreateTermRequest
import com.carry.operation.adapter.inbound.rest.dto.TermResponse
import com.carry.operation.adapter.inbound.rest.dto.UpdateTermRequest
import com.carry.operation.application.port.inbound.CreateTermCommand
import com.carry.operation.application.port.inbound.TermCommandUseCase
import com.carry.operation.application.port.inbound.UpdateTermCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Terms - Admin", description = "이용약관 관리 API")
@RestController
@RequestMapping("/api/v2/admin/terms")
class TermAdminController(
    private val termCommandUseCase: TermCommandUseCase,
) {

    @Operation(summary = "약관 생성")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "약관 생성 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"),
        ],
    )
    @PostMapping
    fun createTerm(
        @Valid @RequestBody request: CreateTermRequest,
    ): ResponseEntity<ApiResponse<TermResponse>> {
        val term = termCommandUseCase.createTerm(
            CreateTermCommand(
                title = request.title,
                content = request.content,
                type = request.type,
                required = request.required,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(TermResponse.from(term)))
    }

    @Operation(summary = "약관 수정")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "약관 수정 성공"),
            SwaggerApiResponse(responseCode = "404", description = "약관을 찾을 수 없음"),
        ],
    )
    @PutMapping("/{termId}")
    fun updateTerm(
        @PathVariable termId: Long,
        @Valid @RequestBody request: UpdateTermRequest,
    ): ResponseEntity<ApiResponse<TermResponse>> {
        val term = termCommandUseCase.updateTerm(
            UpdateTermCommand(
                termId = termId,
                title = request.title,
                content = request.content,
                required = request.required,
            ),
        )
        return ResponseEntity.ok(ApiResponse.success(TermResponse.from(term)))
    }

    @Operation(summary = "약관 비활성화")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "약관 비활성화 성공")])
    @DeleteMapping("/{termId}")
    fun deactivateTerm(
        @PathVariable termId: Long,
    ): ResponseEntity<Void> {
        termCommandUseCase.deactivateTerm(termId)
        return ResponseEntity.noContent().build()
    }
}
