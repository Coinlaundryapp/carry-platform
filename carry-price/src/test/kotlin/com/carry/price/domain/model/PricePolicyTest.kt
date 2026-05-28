package com.carry.price.domain.model

import com.carry.common.exception.BusinessException
import com.carry.common.exception.ErrorCode
import com.carry.price.domain.vo.LaundryItemType
import com.carry.price.domain.vo.OptionPrice
import com.carry.price.domain.vo.OptionType
import com.carry.price.domain.vo.OrderRequestType
import com.carry.price.domain.vo.OrderUnitType
import com.carry.price.domain.vo.PriceCondition
import com.carry.price.domain.vo.SubOptionType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class PricePolicyTest {

    private val condition = PriceCondition(
        OrderUnitType.SOLO,
        OrderRequestType.NEW,
        LaundryItemType.REGULAR,
    )

    private val samplePrices = listOf(
        OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500),
        OptionPrice(OptionType.WASH, SubOptionType.HOT_WATER, 5000),
        OptionPrice(OptionType.DRY, SubOptionType.LOW_HEAT, 4000),
        OptionPrice(OptionType.DRY, SubOptionType.HIGH_HEAT, 4000),
        OptionPrice(OptionType.ADDITIONAL, SubOptionType.FOLD_LAUNDRY, 1000),
        OptionPrice(OptionType.ADDITIONAL, SubOptionType.ADD_SOFTENER, 0),
    )

    private fun createPolicy(prices: List<OptionPrice> = samplePrices) =
        PricePolicy.create(condition, prices)

    private fun reconstitutedPolicy(prices: List<OptionPrice> = samplePrices) =
        PricePolicy.reconstitute(
            id = 1L,
            condition = condition,
            optionPrices = prices,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Nested
    inner class Create {

        @Test
        fun `새 가격 정책을 생성한다`() {
            val policy = createPolicy()

            assertThat(policy.id).isNull()
            assertThat(policy.condition).isEqualTo(condition)
            assertThat(policy.optionPrices).hasSize(6)
        }

        @Test
        fun `빈 옵션으로 생성할 수 있다`() {
            val policy = createPolicy(emptyList())
            assertThat(policy.optionPrices).isEmpty()
        }

        @Test
        fun `중복 옵션이 있으면 생성에 실패한다`() {
            val duplicated = listOf(
                OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500),
                OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 5000),
            )

            assertThatThrownBy { createPolicy(duplicated) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("중복")
        }
    }

    @Nested
    inner class FindOptionPrice {

        @Test
        fun `옵션 가격을 조회한다`() {
            val policy = reconstitutedPolicy()

            val price = policy.findOptionPrice(OptionType.WASH, SubOptionType.STANDARD)

            assertThat(price).isNotNull
            assertThat(price!!.price).isEqualTo(4500)
        }

        @Test
        fun `존재하지 않는 옵션은 null을 반환한다`() {
            val policy = reconstitutedPolicy(emptyList())

            val price = policy.findOptionPrice(OptionType.WASH, SubOptionType.STANDARD)

            assertThat(price).isNull()
        }
    }

    @Nested
    inner class SetOptionPrice {

        @Test
        fun `새 옵션 가격을 추가한다`() {
            val policy = reconstitutedPolicy(emptyList())

            policy.setOptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500, true)

            assertThat(policy.optionPrices).hasSize(1)
            assertThat(policy.findOptionPrice(OptionType.WASH, SubOptionType.STANDARD)!!.price)
                .isEqualTo(4500)
        }

        @Test
        fun `기존 옵션 가격을 덮어쓴다`() {
            val policy = reconstitutedPolicy()

            policy.setOptionPrice(OptionType.WASH, SubOptionType.STANDARD, 9999, false)

            val updated = policy.findOptionPrice(OptionType.WASH, SubOptionType.STANDARD)!!
            assertThat(updated.price).isEqualTo(9999)
            assertThat(updated.selectable).isFalse()
        }
    }

    @Nested
    inner class CalculateTotal {

        @Test
        fun `선택한 옵션의 총액을 계산한다`() {
            val policy = reconstitutedPolicy()

            val total = policy.calculateTotal(
                listOf(
                    OptionType.WASH to SubOptionType.STANDARD,
                    OptionType.DRY to SubOptionType.LOW_HEAT,
                    OptionType.ADDITIONAL to SubOptionType.ADD_SOFTENER,
                ),
            )

            assertThat(total).isEqualTo(4500 + 4000 + 0)
        }

        @Test
        fun `존재하지 않는 옵션을 선택하면 예외가 발생한다`() {
            val policy = reconstitutedPolicy(emptyList())

            assertThatThrownBy {
                policy.calculateTotal(listOf(OptionType.WASH to SubOptionType.STANDARD))
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("존재하지 않는 옵션")
                .extracting("errorCode").isEqualTo(ErrorCode.OPTION_NOT_FOUND)
        }

        @Test
        fun `선택 불가능한 옵션을 선택하면 예외가 발생한다`() {
            val prices = listOf(
                OptionPrice(OptionType.WASH, SubOptionType.HOT_WATER, 5000, selectable = false),
            )
            val policy = reconstitutedPolicy(prices)

            assertThatThrownBy {
                policy.calculateTotal(listOf(OptionType.WASH to SubOptionType.HOT_WATER))
            }.isInstanceOf(BusinessException::class.java)
                .hasMessageContaining("선택 불가능")
                .extracting("errorCode").isEqualTo(ErrorCode.OPTION_NOT_SELECTABLE)
        }
    }

    @Nested
    inner class GetFilteredOptions {

        @Test
        fun `선택 가능한 옵션만 필터링한다`() {
            val prices = listOf(
                OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500, selectable = true),
                OptionPrice(OptionType.WASH, SubOptionType.HOT_WATER, 5000, selectable = false),
            )
            val policy = reconstitutedPolicy(prices)

            assertThat(policy.getSelectableOptions()).hasSize(1)
        }

        @Test
        fun `타입별 옵션을 필터링한다`() {
            val policy = reconstitutedPolicy()

            val washOptions = policy.getOptionsByType(OptionType.WASH)

            assertThat(washOptions).hasSize(2)
            assertThat(washOptions.map { it.subOptionType }).containsExactlyInAnyOrder(
                SubOptionType.STANDARD, SubOptionType.HOT_WATER,
            )
        }
    }
}
