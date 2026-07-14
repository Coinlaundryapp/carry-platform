package com.carry.user.domain.model

import com.carry.common.exception.requireInput
import com.carry.common.exception.checkState
import com.carry.user.domain.vo.Email
import com.carry.user.domain.vo.Phone
import com.carry.user.domain.vo.UserRole
import java.time.Instant

class User private constructor(
    val id: Long?,
    val email: Email,
    val emailVerified: Boolean,
    private var _name: String,
    private var _phone: Phone,
    val role: UserRole,
    private var _active: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val name: String get() = _name
    val phone: Phone get() = _phone
    val isActive: Boolean get() = _active

    fun updateProfile(name: String, phone: Phone) {
        checkState(_active) { "비활성 계정의 프로필을 수정할 수 없습니다" }
        requireInput(name.isNotBlank()) { "이름은 비어있을 수 없습니다" }
        _name = name
        _phone = phone
    }

    fun deactivate() {
        checkState(_active) { "이미 비활성 상태인 계정입니다" }
        _active = false
    }

    companion object {
        fun create(
            email: Email,
            emailVerified: Boolean,
            name: String,
            phone: Phone,
            role: UserRole = UserRole.CUSTOMER,
        ): User {
            requireInput(name.isNotBlank()) { "이름은 비어있을 수 없습니다" }
            return User(
                id = null,
                email = email,
                emailVerified = emailVerified,
                _name = name,
                _phone = phone,
                role = role,
                _active = true,
                createdAt = null,
                updatedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            email: Email,
            emailVerified: Boolean,
            name: String,
            phone: Phone,
            role: UserRole,
            isActive: Boolean,
            createdAt: Instant,
            updatedAt: Instant,
        ): User = User(
            id = id,
            email = email,
            emailVerified = emailVerified,
            _name = name,
            _phone = phone,
            role = role,
            _active = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
