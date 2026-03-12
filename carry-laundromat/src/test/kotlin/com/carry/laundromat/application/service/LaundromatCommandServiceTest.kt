package com.carry.laundromat.application.service

import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.exception.LaundromatNotFoundException
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import com.carry.laundromat.domain.vo.MediaResource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class LaundromatCommandServiceTest {

    private val port = mockk<LaundromatPersistencePort>(relaxed = true)
    private val sut = LaundromatCommandService(port)

    private val address = LaundromatAddress("서울시 강남구 테헤란로 123")
    private val location = Location(37.5665, 126.9780)

    private fun aLaundromat(
        id: Long = 1L,
        options: Set<LaundromatOption> = setOf(LaundromatOption.WASHING_MACHINE),
        mediaResources: List<MediaResource> = emptyList(),
    ) = Laundromat.reconstitute(
        id = id,
        name = "빨래방",
        address = address,
        location = location,
        options = options,
        mediaResources = mediaResources,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class Register {

        @Test
        fun `새 세탁소를 등록한다`() {
            val saved = slot<Laundromat>()
            every { port.save(capture(saved)) } answers { saved.captured }

            val result = sut.register(
                name = "빨래방",
                address = address,
                location = location,
                options = setOf(LaundromatOption.WASHING_MACHINE),
            )

            assertThat(result.name).isEqualTo("빨래방")
            assertThat(result.options).contains(LaundromatOption.WASHING_MACHINE)
        }
    }

    @Nested
    inner class UpdateInfo {

        @Test
        fun `세탁소 정보를 수정한다`() {
            val laundromat = aLaundromat()
            every { port.findById(1L) } returns laundromat
            every { port.save(any()) } answers { firstArg() }

            val newAddress = LaundromatAddress("서울시 서초구 반포대로 45")
            val result = sut.updateInfo(1L, "새빨래방", newAddress, location)

            assertThat(result.name).isEqualTo("새빨래방")
            assertThat(result.address).isEqualTo(newAddress)
        }

        @Test
        fun `존재하지 않는 세탁소를 수정하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.updateInfo(999L, "이름", address, location) }
                .isInstanceOf(LaundromatNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateOptions {

        @Test
        fun `옵션을 일괄 교체한다`() {
            val laundromat = aLaundromat(options = setOf(LaundromatOption.WASHING_MACHINE))
            every { port.findById(1L) } returns laundromat
            every { port.save(any()) } answers { firstArg() }

            val newOptions = setOf(LaundromatOption.DRYER, LaundromatOption.SNEAKERS)
            val result = sut.updateOptions(1L, newOptions)

            assertThat(result.options).containsExactlyInAnyOrder(
                LaundromatOption.DRYER, LaundromatOption.SNEAKERS,
            )
        }
    }

    @Nested
    inner class MediaResourceManagement {

        @Test
        fun `미디어 리소스를 추가한다`() {
            val laundromat = aLaundromat()
            every { port.findById(1L) } returns laundromat
            every { port.save(any()) } answers { firstArg() }

            val result = sut.addMediaResource(1L, "https://s3/img.jpg", "jpg")

            assertThat(result.mediaResources).hasSize(1)
        }

        @Test
        fun `미디어 리소스를 제거한다`() {
            val media = MediaResource(id = 10L, url = "https://s3/img.jpg", extension = "jpg")
            val laundromat = aLaundromat(mediaResources = listOf(media))
            every { port.findById(1L) } returns laundromat
            every { port.save(any()) } answers { firstArg() }

            val result = sut.removeMediaResource(1L, 10L)

            assertThat(result.mediaResources).isEmpty()
        }

        @Test
        fun `존재하지 않는 세탁소에 미디어를 추가하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.addMediaResource(999L, "url", "jpg") }
                .isInstanceOf(LaundromatNotFoundException::class.java)
        }
    }
}
