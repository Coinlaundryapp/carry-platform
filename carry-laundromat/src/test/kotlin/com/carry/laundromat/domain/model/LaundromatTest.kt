package com.carry.laundromat.domain.model

import com.carry.common.exception.BusinessException
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import com.carry.laundromat.domain.vo.MediaResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class LaundromatTest {

    private val address = LaundromatAddress("서울시 강남구 테헤란로 123", "1층", "06234")
    private val location = Location(37.5665, 126.9780)

    private fun createLaundromat(
        name: String = "빨래방",
        options: Set<LaundromatOption> = emptySet(),
    ) = Laundromat.create(name = name, address = address, location = location, options = options)

    private fun reconstitutedLaundromat(
        options: Set<LaundromatOption> = setOf(LaundromatOption.WASHING_MACHINE),
        mediaResources: List<MediaResource> = emptyList(),
    ) = Laundromat.reconstitute(
        id = 1L,
        name = "빨래방",
        address = address,
        location = location,
        options = options,
        mediaResources = mediaResources,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class Create {

        @Test
        fun `새 세탁소를 생성한다`() {
            val laundromat = createLaundromat()

            assertThat(laundromat.id).isNull()
            assertThat(laundromat.name).isEqualTo("빨래방")
            assertThat(laundromat.address).isEqualTo(address)
            assertThat(laundromat.location).isEqualTo(location)
            assertThat(laundromat.options).isEmpty()
            assertThat(laundromat.mediaResources).isEmpty()
        }

        @Test
        fun `옵션을 지정하여 생성한다`() {
            val laundromat = createLaundromat(
                options = setOf(LaundromatOption.WASHING_MACHINE, LaundromatOption.DRYER),
            )
            assertThat(laundromat.options).containsExactlyInAnyOrder(
                LaundromatOption.WASHING_MACHINE, LaundromatOption.DRYER,
            )
        }

        @Test
        fun `빈 이름으로 생성하면 실패한다`() {
            assertThatThrownBy { createLaundromat(name = "") }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("세탁소 이름")
        }
    }

    @Nested
    inner class UpdateInfo {

        @Test
        fun `세탁소 정보를 수정한다`() {
            val laundromat = reconstitutedLaundromat()
            val newAddress = LaundromatAddress("서울시 서초구 반포대로 45")
            val newLocation = Location(37.4950, 127.0100)

            laundromat.updateInfo("새빨래방", newAddress, newLocation)

            assertThat(laundromat.name).isEqualTo("새빨래방")
            assertThat(laundromat.address).isEqualTo(newAddress)
            assertThat(laundromat.location).isEqualTo(newLocation)
        }

        @Test
        fun `빈 이름으로 수정하면 실패한다`() {
            val laundromat = reconstitutedLaundromat()

            assertThatThrownBy { laundromat.updateInfo("", address, location) }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Nested
    inner class OptionManagement {

        @Test
        fun `옵션을 추가한다`() {
            val laundromat = reconstitutedLaundromat(options = emptySet())

            val added = laundromat.addOption(LaundromatOption.SNEAKERS)

            assertThat(added).isTrue()
            assertThat(laundromat.options).contains(LaundromatOption.SNEAKERS)
        }

        @Test
        fun `중복 옵션 추가시 false를 반환한다`() {
            val laundromat = reconstitutedLaundromat(
                options = setOf(LaundromatOption.WASHING_MACHINE),
            )

            val added = laundromat.addOption(LaundromatOption.WASHING_MACHINE)

            assertThat(added).isFalse()
        }

        @Test
        fun `옵션을 제거한다`() {
            val laundromat = reconstitutedLaundromat(
                options = setOf(LaundromatOption.WASHING_MACHINE, LaundromatOption.DRYER),
            )

            laundromat.removeOption(LaundromatOption.DRYER)

            assertThat(laundromat.options).containsOnly(LaundromatOption.WASHING_MACHINE)
        }

        @Test
        fun `옵션을 일괄 교체한다`() {
            val laundromat = reconstitutedLaundromat(
                options = setOf(LaundromatOption.WASHING_MACHINE),
            )

            laundromat.replaceOptions(setOf(LaundromatOption.DRYER, LaundromatOption.SNEAKERS))

            assertThat(laundromat.options).containsExactlyInAnyOrder(
                LaundromatOption.DRYER, LaundromatOption.SNEAKERS,
            )
        }
    }

    @Nested
    inner class MediaManagement {

        @Test
        fun `미디어 리소스를 추가한다`() {
            val laundromat = reconstitutedLaundromat()

            val resource = laundromat.addMediaResource("https://s3/img.jpg", "jpg")

            assertThat(laundromat.mediaResources).hasSize(1)
            assertThat(resource.url).isEqualTo("https://s3/img.jpg")
        }

        @Test
        fun `미디어 리소스를 제거한다`() {
            val media = MediaResource(id = 10L, url = "https://s3/img.jpg", extension = "jpg")
            val laundromat = reconstitutedLaundromat(mediaResources = listOf(media))

            laundromat.removeMediaResource(10L)

            assertThat(laundromat.mediaResources).isEmpty()
        }

        @Test
        fun `존재하지 않는 미디어 리소스 제거시 무시한다`() {
            val laundromat = reconstitutedLaundromat()

            laundromat.removeMediaResource(999L)

            assertThat(laundromat.mediaResources).isEmpty()
        }
    }
}
