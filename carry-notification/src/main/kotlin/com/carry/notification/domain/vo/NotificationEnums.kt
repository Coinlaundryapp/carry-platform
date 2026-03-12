package com.carry.notification.domain.vo

enum class NotificationType {
    ORDER_CREATED,
    DISPATCH_ACCEPTED,
    PICKUP_COMPLETED,
    INVOICE_ISSUED,
    PAYMENT_COMPLETED,
    DELIVERY_COMPLETED,
    NEW_DISPATCH_AVAILABLE,
    DISPATCH_ASSIGNED,
}

enum class NotificationChannel {
    KAKAO_ALARMTALK,
    SMS,
    PUSH,
}

enum class NotificationStatus {
    PENDING, SENT, FAILED;
}
