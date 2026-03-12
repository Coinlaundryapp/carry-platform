package com.carry.price.application.service

import com.carry.price.application.port.outbound.PricePersistencePort
import com.carry.price.domain.exception.PricePolicyNotFoundForConditionException
import com.carry.price.domain.model.PricePolicy
import com.carry.price.domain.vo.LaundryItemType
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.OrderRequestType
import com.carry.price.domain.vo.OrderUnitType
import com.carry.price.domain.vo.PriceCondition
import com.carry.price.domain.vo.SubOptionType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class PriceQueryServiceTest {

    private val port = mockk<PricePersistencePort>()
    private val sut = PriceQueryService(port)

    private val condition = PriceCondition(
        OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR,
    )

    private fun aPolicy() = PricePolicy.reconstitute(
        id = 1L,
        condition = condition,
        optionPrices = listOf(
            OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500),
            OptionPrice(OptionType.DRY, SubOptionType.LOW_HEAT, 4000),
        ),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Test
    fun `조건으로 가격 정책을 조회한다`() {
        every { port.findByCondition(condition) } returns aPolicy()

        val result = sut.getPolicyByCondition(condition)

        assertThat(result.optionPrices).hasSize(2)
    }

    @Test
    fun `조건에 맞는 정책이 없으면 예외가 발생한다`() {
        every { port.findByCondition(condition) } returns null

        assertThatThrownBy { sut.getPolicyByCondition(condition) }
            .isInstanceOf(PricePolicyNotFoundForConditionException::class.java)
    }

    @Test
    fun `총액을 계산한다`() {
        every { port.findByCondition(condition) } returns aPolicy()

        val total = sut.calculateTotal(
            condition,
            listOf(
                OptionType.WASH to SubOptionType.STANDARD,
                OptionType.DRY to SubOptionType.LOW_HEAT,
            ),
        )

        assertThat(total).isEqualTo(8500)
    }
}
