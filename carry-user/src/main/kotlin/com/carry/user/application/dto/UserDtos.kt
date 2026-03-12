package com.carry.user.application.dto

import com.carry.user.domain.model.User
import com.carry.user.domain.model.UserRole

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val phone: String,
    val role: UserRole
) {
    companion object {
        fun from(user: User) = UserProfileResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            phone = user.phone,
            role = user.role
        )
    }
}

data class UpdateProfileCommand(
    val name: String,
    val phone: String
)
