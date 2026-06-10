package com.carry.infra.kafka.consumer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String> {

    /**
     * 이벤트를 원자적으로 선점(claim)한다. 처리 시작 전에 호출한다.
     *
     * `INSERT ... ON CONFLICT (id) DO NOTHING`이라 동시 중복에서도 정확히 한 트랜잭션만
     * 1행을 삽입한다. 반환값 = 영향 행수: **1 = 선점 성공(처리 진행)**, **0 = 이미 처리됨(skip)**.
     *
     * PostgreSQL 한정 구문. 호출자의 @Transactional 경계 안에서 실행되며, block 실패로
     * 트랜잭션이 롤백되면 이 INSERT도 함께 롤백되어 재처리가 가능하다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO processed_events (id, processed_at) VALUES (:id, :processedAt) " +
            "ON CONFLICT (id) DO NOTHING",
        nativeQuery = true,
    )
    fun claim(@Param("id") id: String, @Param("processedAt") processedAt: Instant): Int
}
