package com.carry.infra.kafka.consumer

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "processed_events")
class ProcessedEvent(
    @Id
    val id: String,

    @Column(nullable = false)
    val processedAt: Instant = Instant.now()
)
