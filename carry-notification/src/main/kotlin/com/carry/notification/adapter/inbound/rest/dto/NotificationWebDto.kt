package com.carry.notification.adapter.inbound.rest.dto

import com.carry.notification.domain.model.Notification
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "알림 응답")
data class NotificationResponse(
    @Schema(description = "알림 ID") val id: Long,
    @Schema(description = "수신자 ID") val recipientId: Long,
    @Schema(description = "수신자 연락처") val recipientContact: String,
    @Schema(description = "알림 유형") val type: String,
    @Schema(description = "알림 채널") val channel: String,
    @Schema(description = "제목") val title: String,
    @Schema(description = "내용") val content: String,
    @Schema(description = "상태") val status: String,
    @Schema(description = "참조 유형", nullable = true) val referenceType: String?,
    @Schema(description = "참조 ID", nullable = true) val referenceId: Long?,
    @Schema(description = "발송 시간", nullable = true) val sentAt: Instant?,
    @Schema(description = "실패 사유", nullable = true) val failReason: String?,
    @Schema(description = "생성 시간") val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id!!,
            recipientId = notification.recipientId,
            recipientContact = notification.recipientContact,
            type = notification.type.name,
            channel = notification.channel.name,
            title = notification.title,
            content = notification.content,
            status = notification.status.name,
            referenceType = notification.referenceType,
            referenceId = notification.referenceId,
            sentAt = notification.sentAt,
            failReason = notification.failReason,
            createdAt = notification.createdAt,
        )
    }
}
