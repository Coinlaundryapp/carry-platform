package com.carry.user.application.service

import com.carry.user.application.port.inbound.UserQueryUseCase
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.UserNotFoundException
import com.carry.user.domain.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserQueryService(
    private val userPersistencePort: UserPersistencePort,
) : UserQueryUseCase {

    override fun getProfile(userId: Long): User {
        return userPersistencePort.findById(userId)
            ?: throw UserNotFoundException(userId)
    }
}
