package com.carry.media.domain.exception

import com.carry.media.domain.vo.MediaStatus
import java.util.UUID

class MediaNotFoundException(accessKey: UUID) :
    RuntimeException("미디어를 찾을 수 없습니다: $accessKey")

class InvalidMediaStatusTransitionException(from: MediaStatus, to: MediaStatus) :
    RuntimeException("미디어 상태 전이가 유효하지 않습니다: $from → $to")
