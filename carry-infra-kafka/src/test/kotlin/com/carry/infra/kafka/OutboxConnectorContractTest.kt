package com.carry.infra.kafka

import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Debezium 아웃박스 커넥터(`infra/debezium/register-connector.json`)의 출력 계약을 잠근다.
 *
 * 배경: 라이브 풀스택 e2e(2026-06-06)에서 CDC 파이프라인이 한 번도 동작한 적 없음을 발견.
 * 기존 통합테스트(Testcontainers saga)는 Kafka를 우회하고, EmbeddedKafka 테스트는 합성
 * 리스너만 써서 "실 Debezium 출력 → 실 컨슈머 OutboxEventEnvelope 역직렬화" 경로를 검증한
 * 테스트가 하나도 없었다. 두 가지 버그가 잠복했다:
 *   1) `event.timestamp=created_at` 매핑 + created_at 이 TIMESTAMPTZ → Debezium 이 STRING 으로
 *      직렬화하는데 EventRouter 는 INT64 요구 → 커넥터 task 사망 → 이벤트가 토픽에 안 나감.
 *   2) EventRouter 가 payload 컬럼만 value 로 내보냄 → 모든 컨슈머가 기대하는 OutboxEventEnvelope
 *      (id/aggregateType/eventType/payload …) 와 불일치 → 전 컨슈머 역직렬화 실패.
 *
 * 두 버그는 `additional.placement` 의 envelope 합성 + event.timestamp 제거로 해소했고,
 * 본 테스트가 그 설정과 와이어 포맷을 회귀 방지로 고정한다.
 */
class OutboxConnectorContractTest {

    private val objectMapper = jacksonObjectMapper()

    @Suppress("UNCHECKED_CAST")
    private fun connectorConfig(): Map<String, Any> {
        // 테스트 작업 디렉터리(모듈)에서 위로 올라가며 레포 루트의 커넥터 정의를 찾는다.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "infra/debezium/register-connector.json")
            if (f.exists()) {
                val json = objectMapper.readValue(f, Map::class.java) as Map<String, Any>
                return json["config"] as Map<String, Any>
            }
            dir = dir.parentFile
        }
        error("register-connector.json 을 찾을 수 없습니다")
    }

    @Test
    fun `커넥터는 event_timestamp 를 created_at 에 매핑하지 않는다 - 버그1 회귀 방지`() {
        // created_at 은 TIMESTAMPTZ → Debezium STRING. event.timestamp 는 INT64 를 요구하므로
        // 이 매핑이 있으면 커넥터 task 가 죽는다. 절대 되살리지 말 것.
        val config = connectorConfig()
        assertThat(config).doesNotContainKey("transforms.outbox.table.field.event.timestamp")
    }

    @Test
    fun `커넥터는 OutboxEventEnvelope 필드를 envelope 로 합성한다 - 버그2 회귀 방지`() {
        val config = connectorConfig()
        val placement = config["transforms.outbox.table.fields.additional.placement"] as String

        // 컨슈머의 OutboxEventEnvelope 역직렬화에 필요한 필드들이 value(envelope)에 실려야 한다.
        assertThat(placement)
            .contains("event_type:envelope:eventType")
            .contains("aggregate_type:envelope:aggregateType")
            .contains("aggregate_id:envelope:aggregateId")
            .contains("id:envelope:id")
            .contains("trace_id:envelope:traceId")
            .contains("created_at:envelope:createdAt")

        // payload 는 문자열로 유지되어야 컨슈머가 envelope.payload 를 다시 readValue 할 수 있다.
        assertThat(config["transforms.outbox.table.expand.json.payload"]).isEqualTo("false")
    }

    @Test
    fun `Debezium 이 내보내는 envelope 메시지가 OutboxEventEnvelope 로 역직렬화된다`() {
        // 라이브 e2e(2026-06-06)에서 수정된 커넥터가 carry.Order.events 로 실제 내보낸 메시지.
        val wire = """
            {"payload":"{\"reason\": \"e2e-envelope-fix\", \"orderId\": 999002, \"cancelledBy\": \"COORDINATOR\"}",
             "id":"4d18180d-c8fe-4071-a94b-7877e2119e56","aggregateType":"Order","aggregateId":"999002",
             "eventType":"OrderCancelledEvent","traceId":"trace-e2e-002","createdAt":"2026-06-06T13:04:20.094452Z"}
        """.trimIndent()

        val envelope = objectMapper.readValue(wire, OutboxEventEnvelope::class.java)

        assertThat(envelope.eventType).isEqualTo("OrderCancelledEvent")
        assertThat(envelope.aggregateType).isEqualTo("Order")
        assertThat(envelope.aggregateId).isEqualTo("999002")
        assertThat(envelope.traceId).isEqualTo("trace-e2e-002")
        assertThat(envelope.id).isEqualTo("4d18180d-c8fe-4071-a94b-7877e2119e56")

        // payload 는 문자열이어야 하고, 컨슈머처럼 다시 도메인 이벤트로 파싱 가능해야 한다.
        val payload = objectMapper.readTree(envelope.payload)
        assertThat(payload.get("orderId").asLong()).isEqualTo(999002L)
        assertThat(payload.get("cancelledBy").asText()).isEqualTo("COORDINATOR")
    }

    @Test
    fun `bare payload 출력은 OutboxEventEnvelope 로 역직렬화되지 않는다 - 깨진 포맷 문서화`() {
        // envelope 합성 이전 EventRouter 가 내보내던 형태(payload 컬럼만). 모든 컨슈머가 이걸로 깨졌다.
        val barePayload = "\"{\\\"reason\\\": \\\"x\\\", \\\"orderId\\\": 1, \\\"cancelledBy\\\": \\\"COORDINATOR\\\"}\""

        assertThatThrownBy { objectMapper.readValue(barePayload, OutboxEventEnvelope::class.java) }
            .isInstanceOf(MismatchedInputException::class.java)
    }
}
