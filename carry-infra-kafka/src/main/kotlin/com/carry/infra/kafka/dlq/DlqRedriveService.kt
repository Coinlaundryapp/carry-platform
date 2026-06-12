package com.carry.infra.kafka.dlq

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.common.metrics.MetricsPort
import com.carry.infra.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * DLQ 메시지 재처리(redrive) — 운영자 트리거형 bounded drain.
 *
 * `<원본>.DLQ` 토픽에서 최대 [maxRecords]건을 읽어 원본 토픽으로 재발행한다.
 * 런북(kafka-dlq-nonempty)의 "일시적 외부 장애 회복 후 수동 재처리" 단계의 자동화.
 *
 * 설계 결정:
 *  - **수동 트리거만** 제공한다(자동 주기 재처리 없음). DLQ 도착은 이미 3회 재시도가
 *    소진된 상태라, 원인 해소 판단은 운영자 몫이다(런북 절차 선행 전제).
 *  - **무한 루프 차단**: 재발행마다 [REDRIVE_COUNT_HEADER]를 증가시키고,
 *    [MAX_REDRIVES] 도달 메시지는 재발행하지 않고 보류(park)한다 — poison 메시지가
 *    원본↔DLQ를 영원히 순환하는 것을 막는다. 보류분은 DLQ 토픽에 남아 수동 검토 대상.
 *  - **정확한 오프셋 커밋**: 성공 처리한 레코드까지만 `offset+1`로 commitSync하여,
 *    중간 실패 시에도 미처리분이 다음 호출에서 이어진다(at-least-once).
 */
@Component
class DlqRedriveService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumerFactory: ConsumerFactory<String, String>,
    private val metrics: MetricsPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 재발행 횟수 헤더. kafka_dlt-* (Spring Kafka 진단 헤더)와 구분되는 자체 네임스페이스. */
        const val REDRIVE_COUNT_HEADER = "carry_dlq-redrive-count"
        const val MAX_REDRIVES = 3
        const val MAX_RECORDS_LIMIT = 1000
        private const val REDRIVE_GROUP = "carry-dlq-redrive"
        private val POLL_TIMEOUT: Duration = Duration.ofSeconds(2)
        private const val SEND_TIMEOUT_SECONDS = 10L
    }

    fun redrive(originalTopic: String, maxRecords: Int): DlqRedriveResult {
        if (originalTopic.isBlank() || originalTopic.endsWith(KafkaConfig.DLQ_SUFFIX)) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "topic은 원본 토픽명이어야 합니다(.DLQ 접미사 제외): $originalTopic",
            )
        }
        if (maxRecords !in 1..MAX_RECORDS_LIMIT) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "maxRecords는 1..$MAX_RECORDS_LIMIT 범위여야 합니다: $maxRecords")
        }

        val dlqTopic = originalTopic + KafkaConfig.DLQ_SUFFIX
        var redriven = 0
        var parked = 0

        consumerFactory.createConsumer(REDRIVE_GROUP, "-redrive").use { consumer ->
            val partitions = consumer.partitionsFor(dlqTopic)
                ?.map { TopicPartition(dlqTopic, it.partition()) }
                .orEmpty()
            if (partitions.isEmpty()) return DlqRedriveResult(0, 0)
            consumer.assign(partitions)

            val toCommit = mutableMapOf<TopicPartition, OffsetAndMetadata>()
            try {
                drain@ while (redriven + parked < maxRecords) {
                    val records = consumer.poll(POLL_TIMEOUT)
                    if (records.isEmpty) break

                    for (record in records) {
                        if (redriven + parked >= maxRecords) break@drain

                        val count = redriveCountOf(record.headers())
                        if (count >= MAX_REDRIVES) {
                            parked++
                            metrics.incrementCounter("carry.kafka.dlq.parked", "topic" to originalTopic)
                            log.warn(
                                "DLQ redrive parked: topic={} key={} redriveCount={} — 한도 도달, 수동 검토 필요",
                                originalTopic, record.key(), count,
                            )
                        } else {
                            val headers = RecordHeaders()
                            record.headers().filterNot { it.key() == REDRIVE_COUNT_HEADER }.forEach { headers.add(it) }
                            headers.add(RecordHeader(REDRIVE_COUNT_HEADER, (count + 1).toString().toByteArray()))

                            kafkaTemplate.send(ProducerRecord(originalTopic, null, record.key(), record.value(), headers))
                                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            redriven++
                            metrics.incrementCounter("carry.kafka.dlq.redriven", "topic" to originalTopic)
                        }
                        // 발행 성공(또는 보류 확정) 후에만 커밋 대상에 포함 — 실패 레코드는 다음 호출에서 재시도
                        toCommit[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset() + 1)
                    }
                }
            } finally {
                if (toCommit.isNotEmpty()) consumer.commitSync(toCommit)
            }
        }

        log.info("DLQ redrive done: topic={} redriven={} parked={}", originalTopic, redriven, parked)
        return DlqRedriveResult(redriven, parked)
    }

    private fun redriveCountOf(headers: org.apache.kafka.common.header.Headers): Int =
        headers.lastHeader(REDRIVE_COUNT_HEADER)?.value()?.toString(Charsets.UTF_8)?.toIntOrNull() ?: 0
}

/**
 * @property redriven 원본 토픽으로 재발행된 건수
 * @property parked 재발행 한도([DlqRedriveService.MAX_REDRIVES]) 도달로 보류된 건수(DLQ에 잔류)
 */
data class DlqRedriveResult(val redriven: Int, val parked: Int)
