package com.carry.delivery.domain.model

import com.carry.delivery.domain.vo.DeliveryStepType
import com.carry.delivery.domain.vo.StepStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 배달 스텝 단독 거동 — 증빙(사진·완료시각·메모)은 정산·분쟁의 근거라 한 번 고정되면 바뀌면 안 된다.
 *
 * [Delivery] 의 상태 가드가 중복 호출을 막아 주지만, 그것이 유일한 보호막이면 호출 경로가 하나 늘 때
 * 조용히 깨진다(불변식 카탈로그 §6.2-5). 스텝 자체의 멱등을 여기서 고정한다.
 */
class DeliveryStepTest {

    private val now = Instant.parse("2026-09-18T09:00:00Z")

    @Test
    fun `생성 직후에는 PENDING 이고 증빙이 비어 있다`() {
        val step = DeliveryStep.createPending(DeliveryStepType.PICKUP)

        assertThat(step.status).isEqualTo(StepStatus.PENDING)
        assertThat(step.mediaIds).isEmpty()
        assertThat(step.completedAt).isNull()
        assertThat(step.note).isNull()
    }

    @Test
    fun `완료하면 증빙과 완료 시각이 기록되고 true 를 돌려준다`() {
        val step = DeliveryStep.createPending(DeliveryStepType.PICKUP)

        val completed = step.complete(listOf(1L, 2L), now, note = "문 앞 수거")

        assertThat(completed).isTrue()
        assertThat(step.status).isEqualTo(StepStatus.COMPLETED)
        assertThat(step.mediaIds).containsExactly(1L, 2L)
        assertThat(step.completedAt).isEqualTo(now)
        assertThat(step.note).isEqualTo("문 앞 수거")
    }

    @Test
    fun `이미 완료된 스텝을 다시 완료하면 no-op 이고 기존 증빙이 보존된다`() {
        val step = DeliveryStep.createPending(DeliveryStepType.DELIVERY)
        step.complete(listOf(1L), now, note = "1차")

        val second = step.complete(listOf(99L), now.plusSeconds(3600), note = "2차")

        assertThat(second).isFalse()
        assertThat(step.mediaIds).containsExactly(1L) // 사진이 중복 누적되지 않는다
        assertThat(step.completedAt).isEqualTo(now) // 완료 시각이 덮어써지지 않는다
        assertThat(step.note).isEqualTo("1차")
    }

    @Test
    fun `복원된 완료 스텝도 재완료되지 않는다`() {
        // 재시도·재배달로 DB 에서 살아난 스텝에 같은 요청이 다시 오는 경로.
        val step = DeliveryStep.reconstitute(
            id = 1L,
            deliveryId = 10L,
            stepType = DeliveryStepType.WASHING,
            status = StepStatus.COMPLETED,
            mediaIds = listOf(5L),
            note = "세탁 완료",
            completedAt = now,
        )

        assertThat(step.complete(listOf(6L), now.plusSeconds(60))).isFalse()
        assertThat(step.mediaIds).containsExactly(5L)
        assertThat(step.note).isEqualTo("세탁 완료")
    }

    @Test
    fun `증빙이 없는 스텝도 완료될 수 있다 - 계량처럼 사진이 없는 단계가 있다`() {
        val weighing = DeliveryStep.createPending(DeliveryStepType.WEIGHING)

        assertThat(weighing.complete(emptyList(), now)).isTrue()
        assertThat(weighing.status).isEqualTo(StepStatus.COMPLETED)
        assertThat(weighing.mediaIds).isEmpty()
        assertThat(weighing.completedAt).isEqualTo(now)
    }
}
