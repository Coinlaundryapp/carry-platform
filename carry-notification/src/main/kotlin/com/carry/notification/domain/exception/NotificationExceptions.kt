package com.carry.notification.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode

class NotificationNotFoundException(notificationId: Long) : BusinessException(
    ErrorCode.NOTIFICATION_NOT_FOUND,
    "알림을 찾을 수 없습니다: $notificationId",
)
