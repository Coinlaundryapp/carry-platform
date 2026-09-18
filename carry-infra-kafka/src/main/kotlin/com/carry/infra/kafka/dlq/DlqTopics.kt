package com.carry.infra.kafka.dlq

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.infra.kafka.KafkaConfig

/**
 * DLQ 운영(redrive/purge) 공통 토픽 규칙.
 *
 * 운영 API는 항상 **원본 토픽명**을 입력으로 받는다(`.DLQ` 접미사는 내부에서 붙인다).
 * 실수로 `.DLQ` 토픽명을 직접 넘기면 `<원본>.DLQ.DLQ`를 가리키게 되므로 입력 단계에서 거른다.
 */
object DlqTopics {

    /** 입력이 원본 토픽명인지 검증한다(blank·`.DLQ` 접미사 거부). 통과 시 그대로 반환. */
    fun requireOriginalTopic(topic: String): String {
        if (topic.isBlank() || topic.endsWith(KafkaConfig.DLQ_SUFFIX)) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "topic은 원본 토픽명이어야 합니다(.DLQ 접미사 제외): $topic",
            )
        }
        return topic
    }

    /** 원본 토픽명 → 대응 DLQ 토픽명. */
    fun dlqTopicOf(originalTopic: String): String = originalTopic + KafkaConfig.DLQ_SUFFIX
}
