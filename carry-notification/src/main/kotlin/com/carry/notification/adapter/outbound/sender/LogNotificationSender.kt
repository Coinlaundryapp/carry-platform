package com.carry.notification.adapter.outbound.sender

import com.carry.notification.application.port.outbound.NotificationSenderPort
import com.carry.notification.domain.vo.NotificationChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LogNotificationSender : NotificationSenderPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(channel: NotificationChannel, contact: String, title: String, content: String) {
        log.info("[NOTIFICATION] channel={}, contact={}, title={}, content={}", channel, contact, title, content)
    }
}
