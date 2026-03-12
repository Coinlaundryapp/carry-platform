package com.carry.notification.application.port.outbound

import com.carry.notification.domain.vo.NotificationChannel

interface NotificationSenderPort {
    fun send(channel: NotificationChannel, contact: String, title: String, content: String)
}
