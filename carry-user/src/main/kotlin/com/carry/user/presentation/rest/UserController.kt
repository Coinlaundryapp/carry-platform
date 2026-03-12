package com.carry.user.presentation.rest

import com.carry.common.response.ApiResponse
import com.carry.user.application.dto.UpdateProfileCommand
import com.carry.user.application.dto.UserProfileResponse
import com.carry.user.application.service.UserQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userQueryService: UserQueryService
) {

    @GetMapping("/me")
    fun getMyProfile(@AuthenticationPrincipal userId: Long): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val profile = userQueryService.getProfile(userId)
        return ResponseEntity.ok(ApiResponse.success(profile))
    }

    @PutMapping("/me")
    fun updateMyProfile(
        @AuthenticationPrincipal userId: Long,
        @RequestBody command: UpdateProfileCommand
    ): ResponseEntity<ApiResponse<UserProfileResponse>> {
        val profile = userQueryService.updateProfile(userId, command)
        return ResponseEntity.ok(ApiResponse.success(profile))
    }
}
