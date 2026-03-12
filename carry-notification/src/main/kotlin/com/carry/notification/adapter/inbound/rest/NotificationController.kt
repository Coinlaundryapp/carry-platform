package com.carry.notification.adapter.inbound.rest

import com.carry.notification.adapter.inbound.rest.dto.NotificationResponse
import com.carry.notification.application.port.inbound.NotificationQueryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/notifications")
class NotificationController(
    private val notificationQueryUseCase: NotificationQueryUseCase,
) {

    @GetMapping("/my")
    fun getMyNotifications(
        @RequestParam recipientId: Long,
    ): ResponseEntity<List<NotificationResponse>> {
        val notifications = notificationQueryUseCase.getNotificationsByRecipient(recipientId)
        return ResponseEntity.ok(notifications.map { NotificationResponse.from(it) })
    }

    @GetMapping("/{notificationId}")
    fun getNotification(
        @PathVariable notificationId: Long,
    ): ResponseEntity<NotificationResponse> {
        val notification = notificationQueryUseCase.getNotification(notificationId)
        return ResponseEntity.ok(NotificationResponse.from(notification))
    }
}
