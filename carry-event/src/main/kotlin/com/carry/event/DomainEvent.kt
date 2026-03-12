package com.carry.event

import java.time.Instant
import java.util.UUID

data class DomainEvent<T>(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val aggregateId: String,
    val aggregateType: String,
    val payload: T,
    val occurredAt: Instant = Instant.now(),
    val traceId: String? = null
)
