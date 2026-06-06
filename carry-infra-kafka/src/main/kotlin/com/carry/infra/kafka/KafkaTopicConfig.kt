package com.carry.infra.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

/**
 * Kafka 토픽 선언 정책.
 *
 * 소유권 분리:
 *   - 이벤트 토픽(`carry.*.events`)은 **Debezium(producer)이 소유**한다.
 *     파티션 수는 `infra/debezium/register-connector.json`의
 *     `topic.creation.default.partitions`에서 설정한다.
 *   - DLQ 토픽(`carry.*.events.DLQ`)은 **앱이 소유**한다. 앱의
 *     [DeadLetterPublishingRecoverer]가 발행하는 producer이므로 앱이 토픽을 선언한다.
 *
 * DLQ를 앱이 명시 선언하는 이유:
 *   [KafkaConfig.dlqDestinationResolver]가 원본 record와 **같은 파티션 번호**로 DLQ를
 *   라우팅한다. 따라서 DLQ 토픽은 소스 이벤트 토픽과 동일한 파티션 수를 가져야 한다.
 *   브로커 기본 `num.partitions`(1)에 의존하지 않고 여기서 [PARTITIONS]로 선언해
 *   소스(register-connector.json의 6)와 정합시킨다.
 *
 * ⚠️ [PARTITIONS]와 register-connector.json의 `topic.creation.default.partitions`는
 *    함께 바꿔야 한다(둘 다 6).
 */
@Configuration
class KafkaTopicConfig {

    companion object {
        /** 이벤트/DLQ 토픽 파티션 수. register-connector.json의 topic.creation 값과 일치해야 한다. */
        const val PARTITIONS = 6

        /** 단일 브로커이므로 복제 계수 1. 멀티브로커 HA는 별도 스케일아웃 갭. */
        const val REPLICATION_FACTOR: Short = 1

        /** DLQ를 갖는(=소비되는) 이벤트 토픽 목록. 각 토픽의 `.DLQ`를 앱이 선언한다. */
        val CONSUMED_EVENT_TOPICS: List<String> = listOf(
            "carry.Order.events",
            "carry.Payment.events",
            "carry.Dispatch.events",
            "carry.Delivery.events",
        )

        /**
         * 소비되는 이벤트 토픽마다 대응하는 `.DLQ` 토픽을 소스와 동일한 파티션 수·복제 계수로 만든다.
         * 빈 선언과 분리한 순수 함수라 컨텍스트 없이 단언 가능하다.
         */
        fun dlqTopics(): List<NewTopic> =
            CONSUMED_EVENT_TOPICS.map { source ->
                TopicBuilder.name(source + KafkaConfig.DLQ_SUFFIX)
                    .partitions(PARTITIONS)
                    .replicas(REPLICATION_FACTOR.toInt())
                    .build()
            }
    }

    /**
     * DLQ 토픽을 선언한다. [KafkaAdmin]이 기동 시 브로커에 없으면 생성한다(있으면 그대로 둠).
     */
    @Bean
    fun carryDlqTopics(): KafkaAdmin.NewTopics =
        KafkaAdmin.NewTopics(*dlqTopics().toTypedArray())
}
