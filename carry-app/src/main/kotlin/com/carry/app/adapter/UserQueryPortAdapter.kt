package com.carry.app.adapter

import com.carry.order.application.port.outbound.UserQueryPort
import com.carry.order.domain.vo.OrderShippingAddress
import com.carry.user.application.port.inbound.UserQueryUseCase
import org.springframework.stereotype.Component

/**
 * Cross-module adapter: carry-order -> carry-user
 *
 * Implements the UserQueryPort defined in carry-order by delegating to carry-user's UserQueryUseCase.
 */
@Component
class UserQueryPortAdapter(
    private val userQueryUseCase: UserQueryUseCase,
) : UserQueryPort {

    /**
     * TODO: Phase 4 - carry-user에 배송지 관리 기능 구현 후 실제 주소 조회로 교체
     *  현재는 사용자 프로필 정보를 기반으로 임시 OrderShippingAddress를 생성합니다.
     *  addressId는 아직 사용되지 않습니다.
     */
    override fun getShippingAddress(userId: Long, addressId: Long): OrderShippingAddress {
        val user = userQueryUseCase.getProfile(userId)

        return OrderShippingAddress(
            roadAddress = "배송지 주소 미등록",
            detailAddress = "",
            zipCode = null,
            latitude = 0.0,
            longitude = 0.0,
            recipientName = user.name,
            recipientPhone = user.phone.value,
            entranceInfo = null,
        )
    }
}
