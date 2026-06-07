package com.carry.audit.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogJpaRepository : JpaRepository<AuditLogJpaEntity, Long>
