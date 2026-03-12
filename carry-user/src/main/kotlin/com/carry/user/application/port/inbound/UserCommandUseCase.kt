package com.carry.user.application.port.inbound

import com.carry.user.domain.model.User

interface UserCommandUseCase {

    fun updateProfile(userId: Long, name: String, phone: String): User

    fun deactivate(userId: Long)
}
