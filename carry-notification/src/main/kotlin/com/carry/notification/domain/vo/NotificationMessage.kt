package com.carry.notification.domain.vo

/**
 * 알림 메시지 본문(제목·내용). 함께 채워지는 응집 개념이라 하나의 VO로 묶는다.
 */
data class NotificationMessage(
    val title: String,
    val content: String,
)
