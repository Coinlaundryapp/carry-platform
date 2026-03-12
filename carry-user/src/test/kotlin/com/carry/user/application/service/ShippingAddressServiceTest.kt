package com.carry.user.application.service

import com.carry.user.application.port.outbound.ShippingAddressPersistencePort
import com.carry.user.domain.exception.DefaultAddressDeletionException
import com.carry.user.domain.exception.ShippingAddressLimitExceededException
import com.carry.user.domain.exception.ShippingAddressNotFoundException
import com.carry.user.domain.exception.ShippingAddressNotOwnedException
import com.carry.user.domain.model.ShippingAddress
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class ShippingAddressServiceTest {

    private val port = mockk<ShippingAddressPersistencePort>(relaxed = true)
    private val sut = ShippingAddressService(port)

    private val address = Address("서울시 강남구 테헤란로 123", "4층", "06234")
    private val coords = Coordinates(37.5665, 126.9780)

    private fun anAddress(
        id: Long = 1L,
        userId: Long = 1L,
        isDefault: Boolean = false,
    ) = ShippingAddress.reconstitute(
        id = id,
        userId = userId,
        alias = "집",
        address = address,
        coordinates = coords,
        recipientName = "홍길동",
        recipientPhone = "01012345678",
        entranceInfo = null,
        areaCode = "GANGNAM",
        isDefault = isDefault,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class GetAddresses {

        @Test
        fun `사용자의 배송지 목록을 조회한다`() {
            val addresses = listOf(anAddress(id = 1L), anAddress(id = 2L))
            every { port.findByUserId(1L) } returns addresses

            val result = sut.getAddresses(1L)

            assertThat(result).hasSize(2)
        }

        @Test
        fun `배송지가 없으면 빈 목록을 반환한다`() {
            every { port.findByUserId(1L) } returns emptyList()

            val result = sut.getAddresses(1L)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class CreateAddress {

        @Test
        fun `첫 번째 배송지는 자동으로 기본 배송지가 된다`() {
            every { port.countByUserId(1L) } returns 0L
            val saved = slot<ShippingAddress>()
            every { port.save(capture(saved)) } answers { saved.captured }

            val result = sut.createAddress(1L, "집", address, coords, "홍길동", "01012345678", null, "GANGNAM")

            assertThat(result.isDefault).isTrue()
        }

        @Test
        fun `두 번째 이후 배송지는 기본 배송지가 아니다`() {
            every { port.countByUserId(1L) } returns 1L
            val saved = slot<ShippingAddress>()
            every { port.save(capture(saved)) } answers { saved.captured }

            val result = sut.createAddress(1L, "회사", address, coords, "홍길동", "01012345678", null, "GANGNAM")

            assertThat(result.isDefault).isFalse()
        }

        @Test
        fun `배송지 개수 제한을 초과하면 예외가 발생한다`() {
            every { port.countByUserId(1L) } returns ShippingAddress.MAX_ADDRESSES_PER_USER.toLong()

            assertThatThrownBy { sut.createAddress(1L, "새주소", address, coords, "홍길동", "01012345678", null, "GANGNAM") }
                .isInstanceOf(ShippingAddressLimitExceededException::class.java)
        }
    }

    @Nested
    inner class UpdateAddress {

        @Test
        fun `배송지 정보를 수정한다`() {
            val existing = anAddress(userId = 1L)
            every { port.findById(1L) } returns existing
            val saved = slot<ShippingAddress>()
            every { port.save(capture(saved)) } answers { saved.captured }

            val newAddress = Address("서울시 서초구 반포대로 45", "2층", "06500")
            val result = sut.updateAddress(1L, 1L, "회사", newAddress, coords, "홍길동", "01012345678", null, "GANGNAM")

            assertThat(result.alias).isEqualTo("회사")
            assertThat(result.address).isEqualTo(newAddress)
        }

        @Test
        fun `존재하지 않는 배송지를 수정하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.updateAddress(1L, 999L, "회사", address, coords, "홍길동", "01012345678", null, "GANGNAM") }
                .isInstanceOf(ShippingAddressNotFoundException::class.java)
        }

        @Test
        fun `다른 사용자의 배송지를 수정하면 예외가 발생한다`() {
            val otherUserAddress = anAddress(userId = 2L)
            every { port.findById(1L) } returns otherUserAddress

            assertThatThrownBy { sut.updateAddress(1L, 1L, "회사", address, coords, "홍길동", "01012345678", null, "GANGNAM") }
                .isInstanceOf(ShippingAddressNotOwnedException::class.java)
        }
    }

    @Nested
    inner class DeleteAddress {

        @Test
        fun `일반 배송지를 삭제한다`() {
            val addr = anAddress(isDefault = false)
            every { port.findById(1L) } returns addr

            sut.deleteAddress(1L, 1L)

            verify { port.delete(addr) }
        }

        @Test
        fun `유일한 기본 배송지는 삭제할 수 있다`() {
            val addr = anAddress(isDefault = true)
            every { port.findById(1L) } returns addr
            every { port.countByUserId(1L) } returns 1L

            sut.deleteAddress(1L, 1L)

            verify { port.delete(addr) }
        }

        @Test
        fun `다른 배송지가 있을 때 기본 배송지를 삭제하면 예외가 발생한다`() {
            val addr = anAddress(isDefault = true)
            every { port.findById(1L) } returns addr
            every { port.countByUserId(1L) } returns 3L

            assertThatThrownBy { sut.deleteAddress(1L, 1L) }
                .isInstanceOf(DefaultAddressDeletionException::class.java)
        }

        @Test
        fun `존재하지 않는 배송지를 삭제하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.deleteAddress(1L, 999L) }
                .isInstanceOf(ShippingAddressNotFoundException::class.java)
        }
    }

    @Nested
    inner class SetDefaultAddress {

        @Test
        fun `기존 기본 배송지를 해제하고 새 기본 배송지를 설정한다`() {
            val oldDefault = anAddress(id = 1L, isDefault = true)
            val newDefault = anAddress(id = 2L, isDefault = false)
            every { port.findDefaultByUserId(1L) } returns oldDefault
            every { port.findById(2L) } returns newDefault
            every { port.save(any()) } answers { firstArg() }

            sut.setDefaultAddress(1L, 2L)

            verify(exactly = 2) { port.save(any()) }
        }

        @Test
        fun `기본 배송지가 없을 때 새 기본 배송지를 설정한다`() {
            val addr = anAddress(id = 1L, isDefault = false)
            every { port.findDefaultByUserId(1L) } returns null
            every { port.findById(1L) } returns addr
            every { port.save(any()) } answers { firstArg() }

            sut.setDefaultAddress(1L, 1L)

            verify(exactly = 1) { port.save(any()) }
        }
    }
}
