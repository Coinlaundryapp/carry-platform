package com.carry.infra.kafka.consumer

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * 소비자측 멱등 마커. 키는 **(consumerGroup, eventId)** 복합키다.
 *
 * 하나의 이벤트는 여러 consumer 그룹으로 fan-out된다(예: `OrderCreatedEvent`는 dispatch·
 * notification·operation·payment 모듈이 각자 소비). 각 그룹은 **독립적으로 한 번씩** 처리해야
 * 한다. eventId 단독 키였을 때는 먼저 claim한 그룹이 나머지 그룹을 "중복"으로 굶겨, 사가가
 * 비결정적으로 누락되는 레이스가 있었다(예: notification이 이기면 dispatch가 배차를 못 만듦).
 * consumerGroup을 키에 포함해 그룹별 멱등을 보장한다.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId::class)
class ProcessedEvent(
    @Id
    @Column(name = "consumer_group")
    val consumerGroup: String,

    @Id
    @Column(name = "event_id")
    val eventId: String,

    @Column(nullable = false)
    val processedAt: Instant = Instant.now(),
)

/** [ProcessedEvent] 복합 식별자. JPA가 요구하는 no-arg 기본값 + Serializable. */
data class ProcessedEventId(
    val consumerGroup: String = "",
    val eventId: String = "",
) : Serializable
