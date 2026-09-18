package com.carry.infra.kafka.dlq

import com.carry.common.metrics.MetricsPort
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * DLQ parked(poison) 메시지 폐기(purge) — 운영자 트리거형 물리 회수.
 *
 * redrive([DlqRedriveService])는 한도 도달 poison 메시지를 재발행하지 않고 **parked**시키되
 * offset을 commit해 무한 루프를 막는다. 그 결과 parked 메시지는 redrive 그룹의 committed offset
 * 아래에 물리적으로 잔류하고, 현재는 토픽 retention이 만료될 때까지 남는다.
 *
 * 본 서비스는 **redrive 그룹([DlqRedriveService.REDRIVE_GROUP])의 committed offset까지**
 * `deleteRecords`로 prefix를 물리 절단한다. committed offset **이하**는 두 종류뿐 —
 *  ① 이미 원본 토픽으로 재발행된 copy(잔류물, 삭제 무해)
 *  ② parked poison
 * 둘 다 안전하게 폐기 대상이다. committed offset **이상**(redrive가 아직 처리하지 않은 신규/미처리분)은
 * 절대 건드리지 않는다 — 이것이 "선별 폐기"의 핵심(미처리 보호)이다.
 *
 * 설계 결정:
 *  - **수동 트리거만**(자동 주기 purge 없음). poison 폐기는 운영자가 런북 검토 후 내리는 종결 판단이다.
 *  - **commit이 아니라 물리 절단**: Kafka는 개별 레코드 선택 삭제가 불가하고, commit은 레코드를 물리적으로
 *    제거하지 않는다. 처리완료 지점(committed offset)까지의 prefix만 잘라 잔류물을 회수한다.
 *  - redrive가 한 번도 돌지 않은 토픽(committed offset 없음)은 무삭제(전량 보존).
 */
@Component
class DlqPurgeService(
    private val kafkaAdmin: KafkaAdmin,
    private val metrics: MetricsPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ADMIN_TIMEOUT_SECONDS = 10L
    }

    fun purge(originalTopic: String): DlqPurgeResult {
        DlqTopics.requireOriginalTopic(originalTopic)
        val dlqTopic = DlqTopics.dlqTopicOf(originalTopic)

        AdminClient.create(kafkaAdmin.configurationProperties).use { admin ->
            // 1. redrive 그룹의 DLQ 토픽 파티션별 committed offset (그룹 미존재 시 빈 맵)
            val committed = admin.listConsumerGroupOffsets(DlqRedriveService.REDRIVE_GROUP)
                .partitionsToOffsetAndMetadata()
                .get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .filterKeys { it.topic() == dlqTopic }
            if (committed.isEmpty()) {
                log.info("DLQ purge skipped: topic={} — redrive 미실행(committed offset 없음)", originalTopic)
                return DlqPurgeResult(0)
            }

            // 2. 파티션별 beginningOffset(low watermark)으로 실제 회수 건수 산출
            val beginning = admin.listOffsets(committed.keys.associateWith { OffsetSpec.earliest() })
                .all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            var purged = 0L
            val toDelete = mutableMapOf<TopicPartition, RecordsToDelete>()
            for ((tp, offsetMeta) in committed) {
                val committedOffset = offsetMeta.offset()
                val beginningOffset = beginning[tp]?.offset() ?: 0L
                val count = (committedOffset - beginningOffset).coerceAtLeast(0L)
                if (count > 0) {
                    purged += count
                    toDelete[tp] = RecordsToDelete.beforeOffset(committedOffset)
                }
            }
            if (toDelete.isEmpty()) {
                log.info("DLQ purge: topic={} — 회수 대상 없음(이미 절단됨)", originalTopic)
                return DlqPurgeResult(0)
            }

            // 3. committed offset 미만 prefix 물리 절단
            admin.deleteRecords(toDelete).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            metrics.incrementCounter("carry.kafka.dlq.purged", "topic" to originalTopic)
            log.info("DLQ purge done: topic={} purged={}", originalTopic, purged)
            return DlqPurgeResult(purged.toInt())
        }
    }
}

/**
 * @property purged 물리 절단으로 폐기된 레코드 건수(redrive 그룹 committed offset − beginningOffset 합).
 */
data class DlqPurgeResult(val purged: Int)
