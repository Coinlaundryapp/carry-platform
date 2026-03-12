package com.carry.app.test

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.springframework.jdbc.core.JdbcTemplate

class OutboxEventAssertions(
    private val jdbc: JdbcTemplate,
    @PublishedApi internal val objectMapper: ObjectMapper,
) {

    fun assertOutboxContains(aggregateType: String, eventType: String, aggregateId: String) {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type = ? AND event_type = ? AND aggregate_id = ?",
            Long::class.java,
            aggregateType, eventType, aggregateId,
        )
        assertThat(count).withFailMessage(
            "Expected outbox to contain $eventType for $aggregateType($aggregateId), but found $count"
        ).isGreaterThan(0)
    }

    fun assertOutboxDoesNotContain(aggregateType: String, eventType: String, aggregateId: String) {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type = ? AND event_type = ? AND aggregate_id = ?",
            Long::class.java,
            aggregateType, eventType, aggregateId,
        )
        assertThat(count).withFailMessage(
            "Expected outbox NOT to contain $eventType for $aggregateType($aggregateId), but found $count"
        ).isEqualTo(0)
    }

    inline fun <reified T> readOutboxPayload(aggregateType: String, eventType: String, aggregateId: String): T {
        val payload = getOutboxPayloadJson(aggregateType, eventType, aggregateId)
        return objectMapper.readValue(payload, T::class.java)
    }

    fun getOutboxPayloadJson(aggregateType: String, eventType: String, aggregateId: String): String {
        return jdbc.queryForObject(
            "SELECT payload::text FROM outbox_events WHERE aggregate_type = ? AND event_type = ? AND aggregate_id = ? ORDER BY created_at DESC LIMIT 1",
            String::class.java,
            aggregateType, eventType, aggregateId,
        )!!
    }

    fun outboxCountFor(aggregateType: String, eventType: String): Long {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE aggregate_type = ? AND event_type = ?",
            Long::class.java,
            aggregateType, eventType,
        )!!
    }
}
