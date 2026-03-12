package com.carry.infra.kafka.consumer

data class OutboxEventEnvelope(
    val id: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val traceId: String? = null,
    val createdAt: String? = null,
)
