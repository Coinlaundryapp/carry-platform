package com.carry.user.application.port.inbound

import com.carry.user.domain.model.User
import com.carry.user.domain.vo.OAuthProvider

interface AuthUseCase {

    fun loginOrRegister(
        provider: OAuthProvider,
        oauthId: String,
        email: String,
        name: String,
        phone: String,
    ): User
}
