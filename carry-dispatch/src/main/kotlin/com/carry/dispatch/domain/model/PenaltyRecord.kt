package com.carry.dispatch.domain.model

import com.carry.common.exception.checkInvariant
import com.carry.dispatch.domain.vo.PenaltyReason
import java.time.Instant

class PenaltyRecord private constructor(
    val id: Long?,
    val carrierId: Long,
    val dispatchId: Long,
    val reason: PenaltyReason,
    val createdAt: Instant,
) {
    companion object {
        /** 패널티는 서비스 내부에서만 만들어지므로 깨진 식별자는 우리 버그다 — 400 이 아니라 500. */
        fun create(carrierId: Long, dispatchId: Long, reason: PenaltyReason, now: Instant): PenaltyRecord {
            checkInvariant(carrierId > 0) { "패널티의 캐리어 식별자가 유효하지 않습니다: $carrierId" }
            checkInvariant(dispatchId > 0) { "패널티의 배차 식별자가 유효하지 않습니다: $dispatchId" }
            return PenaltyRecord(null, carrierId, dispatchId, reason, now)
        }

        fun reconstitute(
            id: Long, carrierId: Long, dispatchId: Long, reason: PenaltyReason, createdAt: Instant,
        ): PenaltyRecord = PenaltyRecord(id, carrierId, dispatchId, reason, createdAt)
    }
}
