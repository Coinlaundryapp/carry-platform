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

    private val now = Instant.now()
    private val pickupAt = now.plus(2, ChronoUnit.HOURS)

    private fun createDispatch() = Dispatch.create(
        orderId = 1L,
        laundromatId = 10L,
        areaCode = "GANGNAM",
        desiredPickupAt = pickupAt,
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
            dispatch.claimByCarrier(100L)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            assertThat(dispatch.assignedBy).isEqualTo(AssignedBy.CARRIER)
            assertThat(dispatch.acceptedAt).isNotNull()
        }

        @Test
        fun `PENDING이 아닌 상태에서 클레임하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)

            assertThatThrownBy { dispatch.claimByCarrier(200L) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }
    }

    @Nested
    inner class AssignByCoordinator {

        @Test
        fun `코디네이터가 배차를 지정하면 ASSIGNED 상태가 된다`() {
            val dispatch = createDispatch()
            dispatch.assignByCoordinator(100L)

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ASSIGNED)
            assertThat(dispatch.carrierId).isEqualTo(100L)
            assertThat(dispatch.assignedBy).isEqualTo(AssignedBy.COORDINATOR)
            assertThat(dispatch.assignedAt).isNotNull()
        }

        @Test
        fun `PENDING이 아닌 상태에서 지정하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ACCEPTED, 100L)

            assertThatThrownBy { dispatch.assignByCoordinator(200L) }
                .isInstanceOf(DispatchNotPendingException::class.java)
        }
    }

    @Nested
    inner class AcceptAssignment {

        @Test
        fun `캐리어가 지정된 배차를 수락하면 ACCEPTED 상태가 된다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            dispatch.acceptAssignment()

            assertThat(dispatch.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(dispatch.acceptedAt).isNotNull()
        }

        @Test
        fun `ASSIGNED가 아닌 상태에서 수락하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThatThrownBy { dispatch.acceptAssignment() }
                .isInstanceOf(DispatchAlreadyAcceptedException::class.java)
        }
    }

    @Nested
    inner class RejectAssignment {

        @Test
        fun `캐리어가 지정된 배차를 거절하면 PENDING으로 돌아가고 패널티 기록이 생성된다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.ASSIGNED, 100L)
            val penalty = dispatch.rejectAssignment()

            assertThat(dispatch.status).isEqualTo(DispatchStatus.PENDING)
            assertThat(dispatch.carrierId).isNull()
            assertThat(dispatch.assignedBy).isNull()
            assertThat(penalty.carrierId).isEqualTo(100L)
            assertThat(penalty.dispatchId).isEqualTo(1L)
            assertThat(penalty.reason).isEqualTo(PenaltyReason.REJECTED_FORCED_ASSIGNMENT)
        }

        @Test
        fun `ASSIGNED가 아닌 상태에서 거절하면 예외가 발생한다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThatThrownBy { dispatch.rejectAssignment() }
                .isInstanceOf(DispatchAlreadyAcceptedException::class.java)
        }
    }

    @Nested
    inner class Cancel {

        @Test
        fun `PENDING 상태의 배차를 취소할 수 있다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)
            dispatch.cancel("주문 취소")

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
        fun `이미 취소된 배차는 다시 취소할 수 없다`() {
            val dispatch = Dispatch.reconstitute(
                id = 1L, orderId = 1L, laundromatId = 10L, status = DispatchStatus.CANCELLED,
                carrierId = null, areaCode = "GANGNAM", desiredPickupAt = pickupAt,
                assignedBy = null, assignedAt = null, acceptedAt = null,
                cancelReason = "이전 취소", createdAt = now, updatedAt = now,
            )

            assertThatThrownBy { dispatch.cancel("재취소 시도") }
                .isInstanceOf(DispatchNotCancellableException::class.java)
        }
    }

    @Nested
    inner class Timeout {

        @Test
        fun `PENDING 상태의 배차를 타임아웃 처리할 수 있다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)
            dispatch.timeout()

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
                desiredPickupAt = Instant.now().minus(1, ChronoUnit.HOURS),
                assignedBy = null, assignedAt = null, acceptedAt = null,
                cancelReason = null, createdAt = now, updatedAt = now,
            )

            assertThat(dispatch.isExpired()).isTrue()
        }

        @Test
        fun `수거 희망 시각까지 충분한 시간이 있으면 만료가 아니다`() {
            val dispatch = reconstitutedDispatch(DispatchStatus.PENDING)

            assertThat(dispatch.isExpired()).isFalse()
        }
    }
}
