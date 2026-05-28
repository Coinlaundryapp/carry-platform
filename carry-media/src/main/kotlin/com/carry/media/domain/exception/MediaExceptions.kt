package com.carry.media.domain.exception

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.media.domain.vo.MediaStatus
import java.util.UUID

class MediaNotFoundException(accessKey: UUID) : BusinessException(
    ErrorCode.MEDIA_NOT_FOUND,
    "미디어를 찾을 수 없습니다: $accessKey",
)

class InvalidMediaStatusTransitionException(from: MediaStatus, to: MediaStatus) : BusinessException(
    ErrorCode.INVALID_MEDIA_STATUS_TRANSITION,
    "미디어 상태 전이가 유효하지 않습니다: $from → $to",
)
