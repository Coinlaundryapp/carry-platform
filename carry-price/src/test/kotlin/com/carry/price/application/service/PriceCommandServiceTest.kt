package com.carry.price.application.service

import com.carry.price.application.port.outbound.PricePersistencePort
import com.carry.price.domain.exception.DuplicatePricePolicyException
import com.carry.price.domain.exception.PricePolicyNotFoundException
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
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class PriceCommandServiceTest {

    private val port = mockk<PricePersistencePort>(relaxed = true)
    private val sut = PriceCommandService(port)

    private val condition = PriceCondition(
        OrderUnitType.SOLO, OrderRequestType.NEW, LaundryItemType.REGULAR,
    )

    private fun aPolicy() = PricePolicy.reconstitute(
        id = 1L,
        condition = condition,
        optionPrices = listOf(OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500)),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    @Nested
    inner class CreatePolicy {

        @Test
        fun `새 가격 정책을 생성한다`() {
            every { port.existsByCondition(condition) } returns false
            val saved = slot<PricePolicy>()
            every { port.save(capture(saved)) } answers { saved.captured }

            val prices = listOf(OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500))
            val result = sut.createPolicy(condition, prices)

            assertThat(result.condition).isEqualTo(condition)
            assertThat(result.optionPrices).hasSize(1)
        }

        @Test
        fun `이미 존재하는 조건으로 생성하면 예외가 발생한다`() {
            every { port.existsByCondition(condition) } returns true

            assertThatThrownBy { sut.createPolicy(condition, emptyList()) }
                .isInstanceOf(DuplicatePricePolicyException::class.java)
        }
    }

    @Nested
    inner class UpdateOptionPrices {

        @Test
        fun `옵션 가격을 교체한다`() {
            val policy = aPolicy()
            every { port.findById(1L) } returns policy
            every { port.save(any()) } answers { firstArg() }

            val newPrices = listOf(
                OptionPrice(OptionType.DRY, SubOptionType.HIGH_HEAT, 5000),
            )
            val result = sut.updateOptionPrices(1L, newPrices)

            assertThat(result.optionPrices).hasSize(1)
            assertThat(result.optionPrices[0].subOptionType).isEqualTo(SubOptionType.HIGH_HEAT)
        }

        @Test
        fun `존재하지 않는 정책을 수정하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.updateOptionPrices(999L, emptyList()) }
                .isInstanceOf(PricePolicyNotFoundException::class.java)
        }
    }

    @Nested
    inner class DeletePolicy {

        @Test
        fun `가격 정책을 삭제한다`() {
            every { port.findById(1L) } returns aPolicy()

            sut.deletePolicy(1L)

            verify { port.delete(1L) }
        }

        @Test
        fun `존재하지 않는 정책을 삭제하면 예외가 발생한다`() {
            every { port.findById(999L) } returns null

            assertThatThrownBy { sut.deletePolicy(999L) }
                .isInstanceOf(PricePolicyNotFoundException::class.java)
        }
    }
}
