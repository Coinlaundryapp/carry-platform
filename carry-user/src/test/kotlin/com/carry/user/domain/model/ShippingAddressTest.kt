package com.carry.user.domain.model

import com.carry.common.exception.BusinessException
import com.carry.user.domain.vo.Address
import com.carry.user.domain.vo.Coordinates
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class ShippingAddressTest {

    private val defaultAddress = Address("서울시 강남구 테헤란로 123", "4층", "06234")
    private val defaultCoords = Coordinates(37.5665, 126.9780)

    private fun createAddress(
        alias: String = "집",
        isDefault: Boolean = false,
    ): ShippingAddress = ShippingAddress.create(
        userId = 1L,
        alias = alias,
        address = defaultAddress,
        coordinates = defaultCoords,
        recipientName = "홍길동",
        recipientPhone = "01012345678",
        areaCode = "GANGNAM",
        isDefault = isDefault,
    )

    private fun reconstitutedAddress(
        isDefault: Boolean = false,
    ): ShippingAddress = ShippingAddress.reconstitute(
        id = 1L,
        userId = 1L,
        alias = "집",
        address = defaultAddress,
        coordinates = defaultCoords,
        recipientName = "홍길동",
        recipientPhone = "01012345678",
        entranceInfo = null,
        areaCode = "GANGNAM",
        isDefault = isDefault,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class Create {

        @Test
        fun `새 배송지를 생성한다`() {
            val address = createAddress()

            assertThat(address.id).isNull()
            assertThat(address.userId).isEqualTo(1L)
            assertThat(address.alias).isEqualTo("집")
            assertThat(address.address).isEqualTo(defaultAddress)
            assertThat(address.coordinates).isEqualTo(defaultCoords)
            assertThat(address.isDefault).isFalse()
        }

        @Test
        fun `기본 배송지로 생성할 수 있다`() {
            val address = createAddress(isDefault = true)
            assertThat(address.isDefault).isTrue()
        }

        @Test
        fun `빈 별칭으로 생성하면 실패한다`() {
            assertThatThrownBy { createAddress(alias = "") }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("별칭")
        }

        @Test
        fun `최대 배송지 개수는 10이다`() {
            assertThat(ShippingAddress.MAX_ADDRESSES_PER_USER).isEqualTo(10)
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `배송지 정보를 수정한다`() {
            val address = reconstitutedAddress()
            val newAddress = Address("서울시 서초구 반포대로 45", "2층", "06500")
            val newCoords = Coordinates(37.4950, 127.0100)

            address.update("회사", newAddress, newCoords, "홍길동", "01012345678", null, "GANGNAM")

            assertThat(address.alias).isEqualTo("회사")
            assertThat(address.address).isEqualTo(newAddress)
            assertThat(address.coordinates).isEqualTo(newCoords)
        }

        @Test
        fun `빈 별칭으로 수정하면 실패한다`() {
            val address = reconstitutedAddress()

            assertThatThrownBy {
                address.update("", defaultAddress, defaultCoords, "홍길동", "01012345678", null, "GANGNAM")
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("별칭")
        }
    }

    @Nested
    inner class DefaultManagement {

        @Test
        fun `기본 배송지로 설정한다`() {
            val address = reconstitutedAddress(isDefault = false)

            address.markAsDefault()

            assertThat(address.isDefault).isTrue()
        }

        @Test
        fun `기본 배송지 설정을 해제한다`() {
            val address = reconstitutedAddress(isDefault = true)

            address.unmarkAsDefault()

            assertThat(address.isDefault).isFalse()
        }

        @Test
        fun `기본 배송지가 아닌 배송지의 설정을 해제하면 실패한다`() {
            val address = reconstitutedAddress(isDefault = false)

            assertThatThrownBy { address.unmarkAsDefault() }
                .isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("기본 배송지가 아닌")
        }
    }
}
