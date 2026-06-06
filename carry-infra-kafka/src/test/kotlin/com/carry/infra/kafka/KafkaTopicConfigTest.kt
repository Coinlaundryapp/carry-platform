package com.carry.infra.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaTopicConfigTest {

    @Test
    fun `DLQ 토픽은 소비되는 이벤트 토픽마다 _DLQ 접미사로 선언된다`() {
        val names = KafkaTopicConfig.dlqTopics().map { it.name() }

        assertThat(names).containsExactlyInAnyOrder(
            "carry.Order.events.DLQ",
            "carry.Payment.events.DLQ",
            "carry.Dispatch.events.DLQ",
            "carry.Delivery.events.DLQ",
        )
    }

    @Test
    fun `DLQ 토픽 파티션 수는 소스와 정합하도록 PARTITIONS(6)로 선언된다`() {
        // dlqDestinationResolver가 원본과 같은 파티션 번호로 라우팅하므로
        // DLQ 파티션 수는 이벤트 토픽(register-connector.json topic.creation 6)과 일치해야 한다.
        assertThat(KafkaTopicConfig.PARTITIONS).isEqualTo(6)
        assertThat(KafkaTopicConfig.dlqTopics()).allSatisfy {
            assertThat(it.numPartitions()).isEqualTo(6)
            assertThat(it.replicationFactor()).isEqualTo(1.toShort())
        }
    }
}
