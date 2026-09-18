package com.carry.dispatch.domain.model

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.dispatch.domain.vo.PenaltyReason
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 패널티 레코드는 캐리어 평가의 근거라 잘못된 대상에 붙으면 사람이 손으로 걷어내야 한다.
 * 생성은 서비스 내부(강제 배정 거절)에서만 일어나므로, 깨진 식별자는 클라이언트가 아니라 우리 버그다
 * — 400 이 아니라 500(INTERNAL_ERROR)으로 드러난다(불변식 카탈로그 §6.2-6).
 */
class PenaltyRecordTest {

    private val now = Instant.parse("2026-09-18T00:00:00Z")

    @Test
    fun `정상 식별자로 생성하면 사유와 시각이 기록된다`() {
        val record = PenaltyRecord.create(
            carrierId = 7L, dispatchId = 42L, reason = PenaltyReason.REJECTED_FORCED_ASSIGNMENT, now = now,
        )

        assertThat(record.id).isNull()
        assertThat(record.carrierId).isEqualTo(7L)
        assertThat(record.dispatchId).isEqualTo(42L)
        assertThat(record.reason).isEqualTo(PenaltyReason.REJECTED_FORCED_ASSIGNMENT)
        assertThat(record.createdAt).isEqualTo(now)
    }

    @Test
    fun `캐리어 식별자가 0 이하면 내부 불변식 위반이다`() {
        assertThatThrownBy {
            PenaltyRecord.create(carrierId = 0L, dispatchId = 42L, reason = PenaltyReason.REJECTED_FORCED_ASSIGNMENT, now = now)
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting { (it as BusinessException).errorCode }
            .isEqualTo(ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `배차 식별자가 0 이하면 내부 불변식 위반이다`() {
        assertThatThrownBy {
            PenaltyRecord.create(carrierId = 7L, dispatchId = -1L, reason = PenaltyReason.REJECTED_FORCED_ASSIGNMENT, now = now)
        }.isInstanceOf(BusinessException::class.java)
    }
}
