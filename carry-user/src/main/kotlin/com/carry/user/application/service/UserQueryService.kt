package com.carry.user.application.service

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.user.application.dto.UpdateProfileCommand
import com.carry.user.application.dto.UserProfileResponse
import com.carry.user.application.port.inbound.UserQueryUseCase
import com.carry.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserQueryService(
    private val userRepository: UserRepository
) : UserQueryUseCase {

    override fun getProfile(userId: Long): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "User not found") }
        return UserProfileResponse.from(user)
    }

    @Transactional
    fun updateProfile(userId: Long, command: UpdateProfileCommand): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "User not found") }
        user.updateProfile(command.name, command.phone)
        return UserProfileResponse.from(user)
    }
}
