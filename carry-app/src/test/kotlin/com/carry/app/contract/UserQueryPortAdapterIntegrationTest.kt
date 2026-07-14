package com.carry.app.contract

import com.carry.app.adapter.UserQueryPortAdapter
import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class UserQueryPortAdapterIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var userQueryPortAdapter: UserQueryPortAdapter

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() {
        // FK: 자식 먼저 삭제. user_shipping_addresses·user_oauth_accounts 모두 user_users(id)를 참조한다.
        jdbcTemplate.update("DELETE FROM user_shipping_addresses WHERE id = ?", SMOKE_ADDRESS_ID)
        jdbcTemplate.update("DELETE FROM user_oauth_accounts WHERE user_id = ?", SMOKE_USER_ID)
        jdbcTemplate.update("DELETE FROM user_users WHERE id = ?", SMOKE_USER_ID)
    }

    @Test
    fun `real JPA로 저장한 주소가 어댑터를 통해 9필드 매핑된다`() {
        TestFixtures.insertCustomer(jdbcTemplate, id = SMOKE_USER_ID)
        TestFixtures.insertShippingAddress(jdbcTemplate, userId = SMOKE_USER_ID, id = SMOKE_ADDRESS_ID)

        val result = userQueryPortAdapter.getShippingAddress(SMOKE_USER_ID, SMOKE_ADDRESS_ID)

        assertThat(result.roadAddress).isEqualTo("서울시 강남구 테헤란로 123")
        assertThat(result.detailAddress).isEqualTo("4층")
        assertThat(result.zipCode).isEqualTo("06234")
        assertThat(result.latitude).isEqualTo(37.5065)
        assertThat(result.longitude).isEqualTo(127.0536)
        assertThat(result.recipientName).isEqualTo("테스트고객")
        assertThat(result.recipientPhone).isEqualTo("010-1234-5678")
        assertThat(result.areaCode).isEqualTo(TestFixtures.AREA_CODE)
        // insertShippingAddress는 entrance_info를 넣지 않음 → NULL 매핑(JPA nullable 컬럼 커버)
        assertThat(result.entranceInfo).isNull()
    }

    companion object {
        private const val SMOKE_USER_ID = 90001L
        private const val SMOKE_ADDRESS_ID = 90002L
    }
}
