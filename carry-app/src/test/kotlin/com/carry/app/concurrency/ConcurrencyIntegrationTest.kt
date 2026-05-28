package com.carry.app.concurrency

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.event.order.OrderCreatedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.port.outbound.OrderPersistencePort
import com.carry.order.application.service.OrderCommandService
import com.carry.order.domain.vo.OrderStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(SagaIntegrationTestConfig::class)
class ConcurrencyIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var dispatchCommandService: DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: DispatchPersistencePort
    @Autowired lateinit var orderPersistencePort: OrderPersistencePort
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    private val carrierA = 100L
    private val carrierB = 200L

    @BeforeEach
    fun setUp() {
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertCarrier(jdbc, carrierA)
        TestFixtures.insertCarrier(jdbc, carrierB)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertCarrierArea(jdbc, carrierA)
        TestFixtures.insertCarrierArea(jdbc, carrierB)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    /**
     * 코드 안에서 두 트랜잭션을 동시 시작시켜 race를 만든다.
     * `latch.await()` 으로 두 스레드가 같은 시점에 진입하도록 강제.
     */
    private fun <T> runInParallel(actions: List<() -> T>): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(actions.size)
        val start = CountDownLatch(1)
        val futures = actions.map { action ->
            executor.submit<Result<T>> {
                start.await()
                runCatching { action() }
            }
        }
        start.countDown()
        val results = futures.map { it.get(15, TimeUnit.SECONDS) }
        executor.shutdown()
        return results
    }

    private fun createPendingDispatch(): Long {
        val order = orderCommandService.createOrder(
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

        // Outbox에 있는 OrderCreatedEvent를 saga handler로 직접 흘려 dispatch 생성
        val payload = jdbc.queryForObject(
            "SELECT payload FROM outbox_events WHERE aggregate_type='Order' AND event_type='OrderCreatedEvent' AND aggregate_id=? LIMIT 1",
            String::class.java,
            order.id.toString(),
        )!!
        val event = objectMapper.readValue(payload, OrderCreatedEvent::class.java)
        dispatchSagaHandler.onOrderCreated(event)

        return dispatchPersistencePort.findByOrderId(order.id!!)!!.id!!
    }

    @Nested
    inner class DispatchClaim {

        @Test
        fun `같은 PENDING 배차를 두 캐리어가 동시 claim 시도하면 한 명만 ACCEPTED, 다른 한 명은 OptimisticLockingFailureException`() {
            val dispatchId = createPendingDispatch()

            val results = runInParallel(
                listOf(
                    { dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, carrierA)) },
                    { dispatchCommandService.claimDispatch(ClaimDispatchCommand(dispatchId, carrierB)) },
                ),
            )

            val successes = results.count { it.isSuccess }
            val failures = results.filter { it.isFailure }

            assertThat(successes).isEqualTo(1)
            assertThat(failures).hasSize(1)
            assertThat(failures.first().exceptionOrNull())
                .isInstanceOf(OptimisticLockingFailureException::class.java)

            // 영속 상태는 정확히 ACCEPTED 1건, carrierId는 두 후보 중 하나
            val persisted = dispatchPersistencePort.findById(dispatchId)!!
            assertThat(persisted.status).isEqualTo(DispatchStatus.ACCEPTED)
            assertThat(persisted.carrierId).isIn(carrierA, carrierB)
        }
    }

    @Nested
    inner class OrderCancel {

        @Test
        fun `같은 CREATED 주문을 두 트랜잭션이 동시 cancel 시도하면 한 쪽만 성공`() {
            val order = orderCommandService.createOrder(
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
            val orderId = order.id!!

            val results = runInParallel(
                listOf(
                    { orderCommandService.cancelOrder(orderId, "고객 변심 A", "CUSTOMER") },
                    { orderCommandService.cancelOrder(orderId, "고객 변심 B", "CUSTOMER") },
                ),
            )

            val successes = results.count { it.isSuccess }
            val failures = results.filter { it.isFailure }

            assertThat(successes).isEqualTo(1)
            assertThat(failures).hasSize(1)
            assertThat(failures.first().exceptionOrNull())
                .isInstanceOf(OptimisticLockingFailureException::class.java)

            val persisted = orderPersistencePort.findById(orderId)!!
            assertThat(persisted.status).isEqualTo(OrderStatus.CANCELLED)
        }
    }
}
