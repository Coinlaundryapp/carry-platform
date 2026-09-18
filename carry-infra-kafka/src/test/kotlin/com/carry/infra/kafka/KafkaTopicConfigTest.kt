package com.carry.infra.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaTopicConfigTest {

    private fun config(props: KafkaTopicProperties) = KafkaTopicConfig(props)

    @Test
    fun `DLQ 토픽은 소비되는 이벤트 토픽마다 _DLQ 접미사로 선언된다`() {
        val names = config(KafkaTopicProperties()).dlqTopics().map { it.name() }

        assertThat(names).containsExactlyInAnyOrder(
            "carry.Order.events.DLQ",
            "carry.Payment.events.DLQ",
            "carry.Dispatch.events.DLQ",
            "carry.Delivery.events.DLQ",
        )
    }

    @Test
    fun `DLQ 토픽 파티션 수는 소스와 정합하도록 PARTITIONS(6)로 선언된다`() {
        assertThat(KafkaTopicConfig.PARTITIONS).isEqualTo(6)
        assertThat(config(KafkaTopicProperties()).dlqTopics()).allSatisfy {
            assertThat(it.numPartitions()).isEqualTo(6)
        }
    }

    @Test
    fun `기본값은 단일 브로커 호환 - RF 1, min-insync-replicas 1`() {
        // base/test 환경(데이터클래스 기본값) = 기존 단일 브로커 동작 보존.
        assertThat(config(KafkaTopicProperties()).dlqTopics()).allSatisfy {
            assertThat(it.replicationFactor()).isEqualTo(1.toShort())
            assertThat(it.configs()).containsEntry("min.insync.replicas", "1")
        }
    }

    @Test
    fun `주입된 RF와 min-insync-replicas가 모든 DLQ 토픽에 반영된다 - HA 설정`() {
        val ha = config(KafkaTopicProperties(replicationFactor = 3, minInsyncReplicas = 2))
        assertThat(ha.dlqTopics()).allSatisfy {
            assertThat(it.replicationFactor()).isEqualTo(3.toShort())
            assertThat(it.configs()).containsEntry("min.insync.replicas", "2")
        }
    }
}
