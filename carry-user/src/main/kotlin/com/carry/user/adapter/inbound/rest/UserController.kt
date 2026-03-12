package com.carry.user.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.UpdateProfileRequest
import com.carry.user.adapter.inbound.rest.dto.UserProfileResponse
import com.carry.user.application.port.inbound.UserCommandUseCase
import com.carry.user.application.port.inbound.UserQueryUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "사용자 프로필 관리 API")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userQueryUseCase: UserQueryUseCase,
    private val userCommandUseCase: UserCommandUseCase,
) {

    @Operation(summary = "내 프로필 조회", description = "인증된 사용자의 프로필 정보를 조회합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "프로필 조회 성공"), SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")])
    @GetMapping("/me")
    fun getMyProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = userQueryUseCase.getProfile(userId)
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)))
    }

    @Operation(summary = "내 프로필 수정", description = "인증된 사용자의 프로필 정보를 수정합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "200", description = "프로필 수정 성공"), SwaggerApiResponse(responseCode = "400", description = "잘못된 요청"), SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")])
    @PutMapping("/me")
    fun updateMyProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = userCommandUseCase.updateProfile(userId, request.name, request.phone)
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)))
    }

    @Operation(summary = "회원 탈퇴", description = "인증된 사용자의 계정을 비활성화합니다")
    @ApiResponses(value = [SwaggerApiResponse(responseCode = "204", description = "탈퇴 성공"), SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")])
    @DeleteMapping("/me")
    fun deactivateMyAccount(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Void> {
        userCommandUseCase.deactivate(userId)
        return ResponseEntity.noContent().build()
    }
}
