package com.carry.app.performance

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.OrderQueryUseCase
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import net.ttddyy.dsproxy.QueryCountHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(SagaIntegrationTestConfig::class, QueryCountTestConfig::class)
class QueryCountGuardTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var orderQueryUseCase: OrderQueryUseCase
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() = TestFixtures.truncateAll(jdbc)

    private fun seedOrders(count: Int) {
        repeat(count) {
            orderCommandService.createOrder(
                CreateOrderCommand(
                    customerId = TestFixtures.CUSTOMER_ID,
                    shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                    laundromatId = TestFixtures.LAUNDROMAT_ID,
                    laundryItemType = "NORMAL",
                    selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                    desiredPickupAt = TestFixtures.desiredPickupAt(),
                    desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
                ),
            )
        }
    }

    /** 조회 1회의 SELECT 수를 잰다(생성 쿼리는 clear로 제외). */
    private fun selectsForListQuery(size: Int): Int {
        QueryCountHolder.clear()
        orderQueryUseCase.getOrdersByCustomer(TestFixtures.CUSTOMER_ID, null, size)
        return QueryCountHolder.getGrandTotal().select.toInt()
    }

    @Test
    fun `주문 목록 조회는 결과 건수에 비례해 쿼리가 늘지 않는다 (N+1 부재)`() {
        seedOrders(10)
        val selects10 = selectsForListQuery(50)

        TestFixtures.truncateAll(jdbc)
        setUp()
        seedOrders(20)
        val selects20 = selectsForListQuery(50)

        // N+1이 없으면 건수가 2배여도 SELECT 수는 동일
        // 실측 2026-06-09: 수정 전(N+1) selects10=11, selects20=21 (RED) →
        //                  default_batch_fetch_size=100 적용 후 둘 다 2 (GREEN)
        assertThat(selects20).isEqualTo(selects10)
        // 고정 상한: 목록 1쿼리 + selectedOptions 배치 1쿼리 = 실측 2 SELECT, 여유 +1 = 3
        assertThat(selects10).isLessThanOrEqualTo(3)
    }

    @Test
    fun `주문 생성은 write 경로 쿼리 수가 상한을 넘지 않는다`() {
        QueryCountHolder.clear()
        orderCommandService.createOrder(
            CreateOrderCommand(
                customerId = TestFixtures.CUSTOMER_ID,
                shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                laundromatId = TestFixtures.LAUNDROMAT_ID,
                laundryItemType = "NORMAL",
                selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                desiredPickupAt = TestFixtures.desiredPickupAt(),
                desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
            ),
        )
        val qc = QueryCountHolder.getGrandTotal()
        // 주문 생성 = 검증 SELECT + order/option INSERT + outbox INSERT.
        // 실측 2026-06-09: total=10 (qc.total은 Long → 리터럴에 L). 상한 15 = 실측 10 + 여유 5.
        assertThat(qc.total).isLessThanOrEqualTo(15L)
    }
}
