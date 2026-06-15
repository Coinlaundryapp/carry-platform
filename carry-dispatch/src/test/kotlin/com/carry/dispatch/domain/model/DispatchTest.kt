package com.carry.dispatch.domain.model

import com.carry.dispatch.domain.exception.DispatchAlreadyAcceptedException
import com.carry.dispatch.domain.exception.DispatchNotCancellableException
import com.carry.dispatch.domain.exception.DispatchNotPendingException
import com.carry.dispatch.domain.exception.DispatchTimeoutNotAllowedException
import com.carry.dispatch.domain.vo.AssignedBy
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.dispatch.domain.vo.PenaltyReason
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DispatchTest {

    private val now = Instant.parse("2026-06-07T00:00:00Z")
    private val pickupAt = now.plus(2, ChronoUnit.HOURS)

    private fun createDispatch() = Dispatch.create(
        orderId = 1L,
        laundromatId = 10L,
        areaCode = "GANGNAM",
        desiredPickupAt = pickupAt,
        now = now,
    )

    private fun reconstitutedDispatch(
        status: DispatchStatus = DispatchStatus.PENDING,
        carrierId: Long? = null,
    ) = Dispatch.reconstitute(
        id = 1L, orderId = 1L, laundromatId = 10L, status = status,
        carrierId = carrierId, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
        assignedBy = if (carrierId != null && status == DispatchStatus.ASSIGNED) AssignedBy.COORDINATOR else null,
        assignedAt = if (status == DispatchStatus.ASSIGNED) now else null,
        acceptedAt = null, cancelReason = null, createdAt = now, updatedAt = now,
    )

    @Nested
    inner class ClaimByCarrier {

        @Test
        fun `캐리어가 배차를 클레임하면 ACCEPTED 상태가 된다`() {
            val dispatch = createDispatch()
            dispatch.claimByCarrier(100L, now)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            assertThat(dispatch.assignedBy).isEqualTo(AssignedBy.CARRIER)
            assertThat(dispatch.acceptedAt).isEqualTo(now)
        }

        @Test
        fun `클레임이 실제 전이를 일으키면 true를 반환한다`() {
            val dispatch = createDispatch()
            assertThat(dispatch.claimByCarrier(100L, now)).isTrue()
        }

        @Test
        fun `같은 캐리어가 다시 클레임하면 멱등 no-op이고 false를 반환한다`() {
            val dispatch = createDispatch()
            dispatch.claimByCarrier(100L, now)
            val later = now.plus(1, ChronoUnit.HOURS)

            val second = dispatch.claimByCarrier(100L, later)

            assertThat(second).isFalse()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            // 재시도는 타임스탬프를 갱신하지 않는다(진정한 no-op).
            assertThat(dispatch.acceptedAt).isEqualTo(now)
        }

        @Test
        fun `다른 캐리어가 이미 ACCEPTED된 배차를 클레임하면 예외가 발생한다`() {
            val dispatch = createDispatch()
            dispatch.claimByCarrier(100L, now)

            assertThatThrownBy { dispatch.claimByCarrier(200L, now) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }

        @Test
        fun `PENDING이 아닌 상태에서 클레임하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)

            assertThatThrownBy { dispatch.claimByCarrier(200L, now) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }
    }

    @Nested
    inner class AssignByCoordinator {

        @Test
        fun `코디네이터가 배차를 지정하면 ASSIGNED 상태가 된다`() {
            val dispatch = createDispatch()
            dispatch.assignByCoordinator(100L, now)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ASSIGNED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            assertThat(dispatch.assignedBy).isEqualTo(AssignedBy.COORDINATOR)
            assertThat(dispatch.assignedAt).isEqualTo(now)
        }

        @Test
        fun `지정이 실제 전이를 일으키면 true를 반환한다`() {
            val dispatch = createDispatch()
            assertThat(dispatch.assignByCoordinator(100L, now)).isTrue()
        }

        @Test
        fun `같은 캐리어로 다시 지정하면 멱등 no-op이고 false를 반환한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)

            val second = dispatch.assignByCoordinator(100L, now.plus(1, ChronoUnit.HOURS))

            assertThat(second).isFalse()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.ASSIGNED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            assertThat(dispatch.assignedAt).isEqualTo(now)
        }

        @Test
        fun `다른 캐리어로 이미 ASSIGNED된 배차를 지정하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)

            assertThatThrownBy { dispatch.assignByCoordinator(200L, now) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }

        @Test
        fun `PENDING이 아닌 상태에서 지정하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)

            assertThatThrownBy { dispatch.assignByCoordinator(200L, now) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }
    }

    @Nested
    inner class AcceptAssignment {

        @Test
        fun `캐리어가 지정된 배차를 수락하면 ACCEPTED 상태가 된다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            dispatch.acceptAssignment(now)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.acceptedAt).isEqualTo(now)
        }

        @Test
        fun `수락이 실제 전이를 일으키면 true를 반환한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            assertThat(dispatch.acceptAssignment(now)).isTrue()
        }

        @Test
        fun `이미 ACCEPTED면 다시 수락해도 멱등 no-op이고 false를 반환한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            dispatch.acceptAssignment(now)

            val second = dispatch.acceptAssignment(now.plus(1, ChronoUnit.HOURS))

            assertThat(second).isFalse()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.acceptedAt).isEqualTo(now)
        }

        @Test
        fun `ASSIGNED가 아닌 상태에서 수락하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThatThrownBy { dispatch.acceptAssignment(now) }
                .isInstanceOf(DispatchAlreadyAcceptedException::class.java)
        }
    }

    @Nested
    inner class RejectAssignment {

        @Test
        fun `캐리어가 지정된 배차를 거절하면 PENDING으로 돌아가고 패널티 기록이 생성된다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            val penalty = dispatch.rejectAssignment(now)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.PENDING)
            assertThat(dispatch.carrierId).isNull()
            assertThat(dispatch.assignedBy).isNull()
            assertThat(penalty.carrierId).isEqualTo(100L)
            assertThat(penalty.dispatchId).isEqualTo(1L)
            assertThat(penalty.reason).isEqualTo(PenaltyReason.REJECTED_FORCED_ASSIGNMENT)
            assertThat(penalty.createdAt).isEqualTo(now)
        }

        @Test
        fun `ASSIGNED가 아닌 상태에서 거절하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThatThrownBy { dispatch.rejectAssignment(now) }
                .isInstanceOf(DispatchAlreadyAcceptedException::class.java)
        }
    }

    @Nested
    inner class Cancel {

        @Test
        fun `PENDING 상태의 배차를 취소할 수 있다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)
            val transitioned = dispatch.cancel("주문 취소")

            assertThat(transitioned).isTrue()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.CANCELLED)
            assertThat(dispatch.cancelReason).isEqualTo("주문 취소")
        }

        @Test
        fun `ACCEPTED 상태의 배차를 취소할 수 있다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)
            dispatch.cancel("코디네이터 취소")

            assertThat(dispatch.status).isEqualTo(DispatchStatus.CANCELLED)
        }

        @Test
        fun `이미 취소된 배차를 다시 취소하면 멱등 no-op이고 false를 반환한다`() {
            val dispatch = Dispatch.reconstitute(
                id = 1L, orderId = 1L, laundromatId = 10L, status = DispatchStatus.CANCELLED,
                carrierId = null, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
                assignedBy = null, assignedAt = null, acceptedAt = null,
                cancelReason = "이전 취소", createdAt = now, updatedAt = now,
            )

            val second = dispatch.cancel("재취소 시도")

            assertThat(second).isFalse()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.CANCELLED)
            // 기존 취소 사유를 덮어쓰지 않는다(진정한 no-op).
            assertThat(dispatch.cancelReason).isEqualTo("이전 취소")
        }

        @Test
        fun `취소 불가 상태(TIMEOUT)에서 취소하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.TIMEOUT)

            assertThatThrownBy { dispatch.cancel("취소 시도") }
                .isInstanceOf(DispatchNotCancellableException::class.java)
        }
    }

    @Nested
    inner class Timeout {

        @Test
        fun `PENDING 상태의 배차를 타임아웃 처리할 수 있다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)
            val transitioned = dispatch.timeout()

            assertThat(transitioned).isTrue()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.TIMEOUT)
        }

        @Test
        fun `이미 TIMEOUT이면 다시 타임아웃해도 멱등 no-op이고 false를 반환한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.TIMEOUT)

            val second = dispatch.timeout()

            assertThat(second).isFalse()
            assertThat(dispatch.status).isEqualTo(DispatchStatus.TIMEOUT)
        }

        @Test
        fun `PENDING이 아닌 상태에서 타임아웃 처리하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)

            assertThatThrownBy { dispatch.timeout() }
                .isInstanceOf(DispatchTimeoutNotAllowedException::class.java)
        }
    }

    @Nested
    inner class IsExpired {

        @Test
        fun `수거 희망 시각 30분 전이 지나면 만료로 판단한다`() {
            val dispatch = Dispatch.reconstitute(
                id = 1L, orderId = 1L, laundromatId = 10L, status = DispatchStatus.PENDING,
                carrierId = null, areaCode = "GANGNAM",
                desiredPickupAt = now.minus(1, ChronoUnit.HOURS),
                assignedBy = null, assignedAt = null, acceptedAt = null,
                cancelReason = null, createdAt = now, updatedAt = now,
            )

            assertThat(dispatch.isExpired(now)).isTrue()
        }

        @Test
        fun `수거 희망 시각까지 충분한 시간이 있으면 만료가 아니다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThat(dispatch.isExpired(now)).isFalse()
        }
    }
}
