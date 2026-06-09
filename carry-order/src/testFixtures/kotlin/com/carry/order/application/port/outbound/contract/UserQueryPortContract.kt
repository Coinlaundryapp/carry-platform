package com.carry.order.application.port.outbound.contract

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

abstract class UserQueryPortContract {

    protected abstract fun subject(): UserQueryPort

    /** (userId, addressId)에 expected와 동치인 주소를 준비. expected.zipCode는 non-null·non-blank. */
    protected abstract fun arrangeAddress(userId: Long, addressId: Long, expected: OrderShippingAddress)

    /** (userId, addressId)에 주소가 없는 상태 */
    protected abstract fun arrangeMissing(userId: Long, addressId: Long)

    @Test
    fun `9필드가 정확히 매핑된다`() {
        val expected = OrderShippingAddress(
            roadAddress = "서울시 강남구 테헤란로 123",
            detailAddress = "4층 401호",
            zipCode = "06234",
            latitude = 37.5065,
            longitude = 127.0536,
            recipientName = "홍길동",
            recipientPhone = "01012345678",
            entranceInfo = "현관 비밀번호 1234",
            areaCode = "GANGNAM",
        )
        arrangeAddress(1L, 10L, expected)
        assertThat(subject().getShippingAddress(1L, 10L)).isEqualTo(expected)
    }

    @Test
    fun `nullable entranceInfo가 null로 보존된다`() {
        val expected = OrderShippingAddress(
            roadAddress = "서울시 강남구 테헤란로 123",
            detailAddress = "4층",
            zipCode = "06234",
            latitude = 37.5,
            longitude = 127.0,
            recipientName = "김철수",
            recipientPhone = "01099998888",
            entranceInfo = null,
            areaCode = "GANGNAM",
        )
        arrangeAddress(2L, 20L, expected)
        assertThat(subject().getShippingAddress(2L, 20L).entranceInfo).isNull()
    }

    @Test
    fun `주소가 없으면 예외가 전파된다`() {
        arrangeMissing(3L, 30L)
        assertThatThrownBy { subject().getShippingAddress(3L, 30L) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
