package com.carry.user.application.service

import com.carry.user.application.port.inbound.UserCommandUseCase
import com.carry.user.application.port.outbound.UserPersistencePort
import com.carry.user.domain.exception.UserNotFoundException
import com.carry.user.domain.model.User
import com.carry.user.domain.vo.Phone
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserCommandService(
    private val userPersistencePort: UserPersistencePort,
) : UserCommandUseCase {

    override fun updateProfile(userId: Long, name: String, phone: String): User {
        val user = userPersistencePort.findById(userId)
            ?: throw UserNotFoundException(userId)

        user.updateProfile(name, Phone(phone))
        return userPersistencePort.save(user)
    }

    override fun deactivate(userId: Long) {
        val user = userPersistencePort.findById(userId)
            ?: throw UserNotFoundException(userId)

        user.deactivate()
        userPersistencePort.save(user)
    }
}
