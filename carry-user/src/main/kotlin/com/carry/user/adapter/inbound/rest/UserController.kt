package com.carry.user.adapter.inbound.rest

import com.carry.common.response.ApiResponse
import com.carry.user.adapter.inbound.rest.dto.UpdateProfileRequest
import com.carry.user.adapter.inbound.rest.dto.UserProfileResponse
import com.carry.user.application.port.inbound.UserCommandUseCase
import com.carry.user.application.port.inbound.UserQueryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userQueryUseCase: UserQueryUseCase,
    private val userCommandUseCase: UserCommandUseCase,
) {

    @GetMapping("/me")
    fun getMyProfile(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = userQueryUseCase.getProfile(userId)
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)))
    }

    @PutMapping("/me")
    fun updateMyProfile(
        @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val user = userCommandUseCase.updateProfile(userId, request.name, request.phone)
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)))
    }

    @DeleteMapping("/me")
    fun deactivateMyAccount(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<Void> {
        userCommandUseCase.deactivate(userId)
        return ResponseEntity.noContent().build()
    }
}
