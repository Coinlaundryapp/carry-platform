package com.carry.user.application.port.inbound

import com.carry.user.application.dto.UserProfileResponse

interface UserQueryUseCase {
    fun getProfile(userId: Long): UserProfileResponse
}
