package com.carry.review.adapter.outbound.persistence.repository

import com.carry.review.adapter.outbound.persistence.entity.ReviewJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReviewJpaRepository : JpaRepository<ReviewJpaEntity, Long> {

    @Query(
        "SELECT r FROM ReviewJpaEntity r WHERE r.laundromatId = :laundromatId" +
            " AND (:cursor IS NULL OR r.id < :cursor) ORDER BY r.id DESC",
    )
    fun findByLaundromatIdWithCursor(
        laundromatId: Long,
        cursor: Long?,
        pageable: Pageable,
    ): List<ReviewJpaEntity>

    @Query(
        "SELECT r FROM ReviewJpaEntity r WHERE r.customerId = :customerId" +
            " AND (:cursor IS NULL OR r.id < :cursor) ORDER BY r.id DESC",
    )
    fun findByCustomerIdWithCursor(
        customerId: Long,
        cursor: Long?,
        pageable: Pageable,
    ): List<ReviewJpaEntity>

    fun countByLaundromatId(laundromatId: Long): Long

    @Query("SELECT AVG(r.rating) FROM ReviewJpaEntity r WHERE r.laundromatId = :laundromatId")
    fun averageRatingByLaundromatId(laundromatId: Long): Double?
}
