package com.carry.notification.domain.vo

/**
 * 알림이 가리키는 참조 대상(유형·ID). 유형과 ID는 "둘 다 또는 없음"으로만 의미가 있으므로
 * 하나의 VO로 묶어 한쪽만 채워지는 부정합 상태를 타입으로 차단한다.
 */
data class NotificationReference(
    val type: String,
    val id: Long,
)
