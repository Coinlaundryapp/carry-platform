package com.carry.operation.application.service

import com.carry.operation.application.port.inbound.CreateTermCommand
import com.carry.operation.application.port.inbound.UpdateTermCommand
import com.carry.operation.application.port.outbound.TermPersistencePort
import com.carry.operation.domain.exception.TermNotFoundException
import com.carry.operation.domain.model.Term
import com.carry.operation.domain.vo.TermType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class TermCommandServiceTest {

    private val termPersistencePort = mockk<TermPersistencePort>(relaxed = true)
    private val sut = TermCommandService(termPersistencePort)

    private val now = Instant.now()

    private fun reconstitutedTerm() = Term.reconstitute(
        id = 1L, title = "서비스 이용약관", content = "약관 내용",
        type = TermType.SERVICE, required = true, version = 1,
        active = true, createdAt = now, updatedAt = now,
    )

    @Nested
    inner class CreateTerm {

        @Test
        fun `약관을 생성하면 저장소에 저장된다`() {
            val saved = slot<Term>()
            every { termPersistencePort.save(capture(saved)) } answers {
                Term.reconstitute(
                    id = 1L, title = saved.captured.title, content = saved.captured.content,
                    type = saved.captured.type, required = saved.captured.required,
                    version = saved.captured.version, active = saved.captured.active,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.createTerm(
                CreateTermCommand(
                    title = "서비스 이용약관",
                    content = "약관 내용입니다.",
                    type = TermType.SERVICE,
                    required = true,
                ),
            )

            assertThat(result.id).isEqualTo(1L)
            assertThat(result.title).isEqualTo("서비스 이용약관")
            assertThat(result.type).isEqualTo(TermType.SERVICE)
            assertThat(result.version).isEqualTo(1)
            assertThat(result.active).isTrue()
            verify { termPersistencePort.save(any()) }
        }
    }

    @Nested
    inner class UpdateTerm {

        @Test
        fun `약관을 수정하면 버전이 증가하고 저장된다`() {
            val term = reconstitutedTerm()
            every { termPersistencePort.findById(1L) } returns term
            val saved = slot<Term>()
            every { termPersistencePort.save(capture(saved)) } answers {
                Term.reconstitute(
                    id = 1L, title = saved.captured.title, content = saved.captured.content,
                    type = saved.captured.type, required = saved.captured.required,
                    version = saved.captured.version, active = saved.captured.active,
                    createdAt = now, updatedAt = now,
                )
            }

            val result = sut.updateTerm(
                UpdateTermCommand(termId = 1L, title = "수정된 제목", content = "수정된 내용", required = false),
            )

            assertThat(result.version).isEqualTo(2)
            assertThat(result.required).isFalse()
        }

        @Test
        fun `존재하지 않는 약관을 수정하면 예외가 발생한다`() {
            every { termPersistencePort.findById(999L) } returns null

            assertThatThrownBy {
                sut.updateTerm(UpdateTermCommand(termId = 999L, title = "제목", content = "내용", required = true))
            }.isInstanceOf(TermNotFoundException::class.java)
        }
    }

    @Nested
    inner class DeactivateTerm {

        @Test
        fun `약관을 비활성화하면 저장소에 저장된다`() {
            val term = reconstitutedTerm()
            every { termPersistencePort.findById(1L) } returns term

            sut.deactivateTerm(1L)

            verify { termPersistencePort.save(any()) }
        }

        @Test
        fun `존재하지 않는 약관을 비활성화하면 예외가 발생한다`() {
            every { termPersistencePort.findById(999L) } returns null

            assertThatThrownBy { sut.deactivateTerm(999L) }
                .isInstanceOf(TermNotFoundException::class.java)
        }
    }
}
