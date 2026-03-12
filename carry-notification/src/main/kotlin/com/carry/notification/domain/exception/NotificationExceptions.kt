package com.carry.notification.domain.exception

class NotificationNotFoundException(notificationId: Long) :
    RuntimeException("알림을 찾을 수 없습니다: $notificationId")
