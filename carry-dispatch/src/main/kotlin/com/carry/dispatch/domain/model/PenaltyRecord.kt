package com.carry.dispatch.domain.model

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
        fun create(carrierId: Long, dispatchId: Long, reason: PenaltyReason, now: Instant): PenaltyRecord {
            return PenaltyRecord(null, carrierId, dispatchId, reason, now)
        }

        fun reconstitute(
            id: Long, carrierId: Long, dispatchId: Long, reason: PenaltyReason, createdAt: Instant,
        ): PenaltyRecord = PenaltyRecord(id, carrierId, dispatchId, reason, createdAt)
    }
}
