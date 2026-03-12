package com.carry.user.application.service

import com.carry.user.application.dto.UserProfileResponse
import com.carry.user.domain.model.OAuthProvider
import com.carry.user.domain.model.User
import com.carry.user.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository
) {

    @Transactional
    fun loginOrRegister(provider: OAuthProvider, oauthId: String, email: String, name: String, phone: String): UserProfileResponse {
        val user = userRepository.findByOauthProviderAndOauthId(provider, oauthId)
            .orElseGet {
                userRepository.save(
                    User(
                        email = email,
                        name = name,
                        phone = phone,
                        oauthProvider = provider,
                        oauthId = oauthId
                    )
                )
            }
        return UserProfileResponse.from(user)
    }
}
