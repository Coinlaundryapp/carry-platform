package com.carry.price.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OptionPriceTest {

    @Test
    fun `유효한 옵션 가격을 생성한다`() {
        val price = OptionPrice(OptionType.WASH, SubOptionType.STANDARD, 4500)
        assertThat(price.price).isEqualTo(4500)
        assertThat(price.selectable).isTrue()
    }

    @Test
    fun `무료 옵션을 생성할 수 있다`() {
        val price = OptionPrice(OptionType.ADDITIONAL, SubOptionType.ADD_SOFTENER, 0)
        assertThat(price.price).isEqualTo(0)
    }

    @Test
    fun `음수 가격은 거부한다`() {
        assertThatThrownBy { OptionPrice(OptionType.WASH, SubOptionType.STANDARD, -1) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("0 이상")
    }

    @Test
    fun `선택 불가능한 옵션을 생성할 수 있다`() {
        val price = OptionPrice(OptionType.WASH, SubOptionType.HOT_WATER, 5000, selectable = false)
        assertThat(price.selectable).isFalse()
    }
}
