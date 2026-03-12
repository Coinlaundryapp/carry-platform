package com.carry.operation.domain.model

import com.carry.operation.domain.vo.TermType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class TermTest {

    @Nested
    inner class Create {

        @Test
        fun `약관을 생성하면 활성 상태이고 버전이 1이다`() {
            val term = Term.create(
                title = "서비스 이용약관",
                content = "약관 내용입니다.",
                type = TermType.SERVICE,
                required = true,
            )

            assertThat(term.id).isNull()
            assertThat(term.title).isEqualTo("서비스 이용약관")
            assertThat(term.content).isEqualTo("약관 내용입니다.")
            assertThat(term.type).isEqualTo(TermType.SERVICE)
            assertThat(term.required).isTrue()
            assertThat(term.version).isEqualTo(1)
            assertThat(term.active).isTrue()
            assertThat(term.createdAt).isNotNull()
            assertThat(term.updatedAt).isNotNull()
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `약관을 수정하면 버전이 증가한다`() {
            val term = reconstitutedTerm()
            val previousVersion = term.version

            term.update(title = "수정된 제목", content = "수정된 내용", required = false)

            assertThat(term.version).isEqualTo(previousVersion + 1)
            assertThat(term.required).isFalse()
        }

        @Test
        fun `약관을 여러 번 수정하면 버전이 누적 증가한다`() {
            val term = reconstitutedTerm()

            term.update(title = "수정1", content = "내용1", required = true)
            term.update(title = "수정2", content = "내용2", required = false)

            assertThat(term.version).isEqualTo(3)
        }
    }

    @Nested
    inner class Deactivate {

        @Test
        fun `약관을 비활성화하면 active가 false가 된다`() {
            val term = reconstitutedTerm()

            term.deactivate()

            assertThat(term.active).isFalse()
        }

        @Test
        fun `비활성화된 약관을 활성화하면 active가 true가 된다`() {
            val term = reconstitutedTerm(active = false)

            term.activate()

            assertThat(term.active).isTrue()
        }
    }

    @Nested
    inner class VersionIncrement {

        @Test
        fun `수정할 때마다 updatedAt이 갱신된다`() {
            val term = reconstitutedTerm()
            val previousUpdatedAt = term.updatedAt

            term.update(title = "새 제목", content = "새 내용", required = true)

            assertThat(term.updatedAt).isAfterOrEqualTo(previousUpdatedAt)
        }
    }

    private fun reconstitutedTerm(
        active: Boolean = true,
    ) = Term.reconstitute(
        id = 1L,
        title = "서비스 이용약관",
        content = "약관 내용입니다.",
        type = TermType.SERVICE,
        required = true,
        version = 1,
        active = active,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
