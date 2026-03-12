package com.carry.user.application.port.inbound

import com.carry.user.domain.model.User

interface UserQueryUseCase {

    fun getProfile(userId: Long): User
}
