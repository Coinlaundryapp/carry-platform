package com.carry.user.adapter.outbound.persistence.repository

import com.carry.user.adapter.outbound.persistence.entity.UserJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {

    fun findByEmail(email: String): Optional<UserJpaEntity>

    fun existsByEmail(email: String): Boolean
}
