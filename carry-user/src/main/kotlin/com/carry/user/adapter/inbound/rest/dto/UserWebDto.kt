package com.carry.user.adapter.inbound.rest.dto

import com.carry.user.domain.model.User

data class UpdateProfileRequest(
    val name: String,
    val phone: String,
)

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val name: String,
    val phone: String,
    val role: String,
    val isActive: Boolean,
) {
    companion object {
        fun from(user: User) = UserProfileResponse(
            id = user.id!!,
            email = user.email.value,
            name = user.name,
            phone = user.phone.value,
            role = user.role.name,
            isActive = user.isActive,
        )
    }
}
