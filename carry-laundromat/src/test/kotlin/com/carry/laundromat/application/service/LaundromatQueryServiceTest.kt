package com.carry.laundromat.application.service

import com.carry.laundromat.application.port.outbound.LaundromatPersistencePort
import com.carry.laundromat.domain.exception.LaundromatNotFoundException
import com.carry.laundromat.domain.model.Laundromat
import com.carry.laundromat.domain.model.NearbyLaundromat
import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class LaundromatQueryServiceTest {

    private val port = mockk<LaundromatPersistencePort>()
    private val sut = LaundromatQueryService(port)

    private fun aLaundromat(id: Long = 1L) = Laundromat.reconstitute(
        id = id,
        name = "빨래방",
        address = LaundromatAddress("서울시 강남구 테헤란로 123"),
        location = Location(37.5665, 126.9780),
        options = setOf(LaundromatOption.WASHING_MACHINE),
        mediaResources = emptyList(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `ID로 세탁소를 조회한다`() {
        every { port.findById(1L) } returns aLaundromat()

        val result = sut.getById(1L)

        assertThat(result.name).isEqualTo("빨래방")
    }

    @Test
    fun `존재하지 않는 세탁소를 조회하면 예외가 발생한다`() {
        every { port.findById(999L) } returns null

        assertThatThrownBy { sut.getById(999L) }
            .isInstanceOf(LaundromatNotFoundException::class.java)
    }

    @Test
    fun `주변 세탁소를 검색한다`() {
        val nearbyList = listOf(
            NearbyLaundromat(aLaundromat(1L), 500.0),
            NearbyLaundromat(aLaundromat(2L), 1200.0),
        )
        every { port.findNearby(37.5, 127.0, 3000) } returns nearbyList

        val result = sut.findNearby(37.5, 127.0, 3000)

        assertThat(result).hasSize(2)
        assertThat(result[0].distanceMeters).isEqualTo(500.0)
    }

    @Test
    fun `주변에 세탁소가 없으면 빈 목록을 반환한다`() {
        every { port.findNearby(0.0, 0.0, 100) } returns emptyList()

        val result = sut.findNearby(0.0, 0.0, 100)

        assertThat(result).isEmpty()
    }
}
