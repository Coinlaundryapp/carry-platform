package com.carry.infra.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

/**
 * Kafka 토픽 복제 정책(환경별 기변값).
 *
 * - 단일 브로커(base/test/클라우드 미준비 dev·prod): 기본값 1/1.
 * - 멀티브로커 HA(local, 클라우드 준비 후 dev·prod): 3/2 → 브로커 1대 다운 허용(무손실).
 *
 * 프로듀서는 이미 `acks=all`이라 RF/min-ISR만 올리면 무손실이 성립한다.
 */
@ConfigurationProperties(prefix = "carry.kafka")
data class KafkaTopicProperties(
    /** 토픽 복제 계수. 단일 브로커=1, 멀티브로커 HA=3. */
    val replicationFactor: Short = 1,
    /** acks=all 충족에 필요한 최소 ISR. RF=3 HA에서 2(=1대 다운 허용). */
    val minInsyncReplicas: Int = 1,
)

/**
 * Kafka 토픽 선언 정책.
 *
 * 소유권 분리:
 *   - 이벤트 토픽(`carry.*.events`)은 **Debezium(producer)이 소유**한다.
 *     파티션/복제 계수는 `infra/debezium/register-connector.json`의 `topic.creation.*`에서 설정한다.
 *   - DLQ 토픽(`carry.*.events.DLQ`)은 **앱이 소유**한다. 앱의
 *     [DeadLetterPublishingRecoverer]가 발행하는 producer이므로 앱이 토픽을 선언한다.
 *
 * DLQ를 앱이 명시 선언하는 이유:
 *   [KafkaConfig.dlqDestinationResolver]가 원본 record와 **같은 파티션 번호**로 DLQ를
 *   라우팅한다. 따라서 DLQ 토픽은 소스 이벤트 토픽과 동일한 파티션 수를 가져야 한다.
 *
 * 복제 계수·min.insync.replicas는 [KafkaTopicProperties]로 환경별 주입한다(멀티브로커 HA 갭).
 * ⚠️ [PARTITIONS]와 register-connector.json의 `topic.creation.default.partitions`는
 *    함께 바꿔야 한다(둘 다 6). RF는 register-connector.json
 *    `topic.creation.default.replication.factor`와 같은 정책을 공유한다
 *    (이벤트 토픽은 Debezium이, DLQ는 여기서 선언).
 */
@Configuration
class KafkaTopicConfig(
    private val properties: KafkaTopicProperties,
) {

    companion object {
        /** 이벤트/DLQ 토픽 파티션 수. register-connector.json의 topic.creation 값과 일치해야 한다. */
        const val PARTITIONS = 6

        /** DLQ를 갖는(=소비되는) 이벤트 토픽 목록. 각 토픽의 `.DLQ`를 앱이 선언한다. */
        val CONSUMED_EVENT_TOPICS: List<String> = listOf(
            "carry.Order.events",
            "carry.Payment.events",
            "carry.Dispatch.events",
            "carry.Delivery.events",
        )
    }

    /**
     * 소비되는 이벤트 토픽마다 대응하는 `.DLQ` 토픽을 소스와 동일한 파티션 수·주입 복제 계수로 만든다.
     * min.insync.replicas는 토픽 단위 설정으로 부여한다.
     */
    fun dlqTopics(): List<NewTopic> =
        CONSUMED_EVENT_TOPICS.map { source ->
            TopicBuilder.name(source + KafkaConfig.DLQ_SUFFIX)
                .partitions(PARTITIONS)
                .replicas(properties.replicationFactor.toInt())
                .config("min.insync.replicas", properties.minInsyncReplicas.toString())
                .build()
        }

    /**
     * DLQ 토픽을 선언한다. [KafkaAdmin]이 기동 시 브로커에 없으면 생성한다(있으면 그대로 둠).
     */
    @Bean
    fun carryDlqTopics(): KafkaAdmin.NewTopics =
        KafkaAdmin.NewTopics(*dlqTopics().toTypedArray())
}
