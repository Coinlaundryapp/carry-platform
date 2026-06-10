package com.carry.app.concurrency

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.delivery.adapter.outbound.persistence.entity.DeliveryJpaEntity
import com.carry.delivery.adapter.outbound.persistence.repository.DeliveryJpaRepository
import com.carry.delivery.domain.vo.DeliveryStatus
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
import com.carry.payment.adapter.outbound.persistence.entity.PaymentJpaEntity
import com.carry.payment.adapter.outbound.persistence.repository.PaymentJpaRepository
import com.carry.payment.domain.vo.PaymentStatus
import com.carry.payment.domain.vo.PgProvider
import com.carry.review.adapter.outbound.persistence.entity.ReviewJpaEntity
import com.carry.review.adapter.outbound.persistence.repository.ReviewJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
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
    @Autowired lateinit var paymentJpaRepository: PaymentJpaRepository
    @Autowired lateinit var deliveryJpaRepository: DeliveryJpaRepository
    @Autowired lateinit var reviewJpaRepository: ReviewJpaRepository
    @Autowired lateinit var transactionManager: PlatformTransactionManager
    private val tx by lazy { TransactionTemplate(transactionManager) }

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

    // --- @Version 낙관적 락 일관 적용 (Payment·Delivery·Review) ---

    private fun seedInvoiceId(orderId: Long): Long =
        jdbc.queryForObject(
            "INSERT INTO payment_invoices(order_id, customer_id, status, weight, total_amount) " +
                "VALUES (?, 1, 'ISSUED', 1.00, 1000) RETURNING id",
            Long::class.java, orderId,
        )!!

    @Test
    fun `같은 Payment를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val invoiceId = seedInvoiceId(orderId = 90001L)
        val id = tx.execute {
            paymentJpaRepository.save(
                PaymentJpaEntity(
                    invoiceId = invoiceId, orderId = 90001L, customerId = 1L,
                    status = PaymentStatus.PENDING, pgProvider = PgProvider.TOSS_PAYMENTS,
                    pgTransactionId = null, amount = 1000L, paidAt = null, failReason = null,
                ),
            ).id
        }!!
        val stale = tx.execute { paymentJpaRepository.findById(id).get() }!!          // detached, v0
        // step3: fresh 로드 후 스칼라 변경 → dirty-checking이 커밋 시 flush, version 0→1
        tx.execute { paymentJpaRepository.findById(id).get().apply { failReason = "first" } }
        // step4: detached stale(v0) 변경 후 save → merge가 version 불일치 감지
        stale.failReason = "second"
        assertThatThrownBy { tx.execute { paymentJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }

    @Test
    fun `같은 Delivery를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val id = tx.execute {
            deliveryJpaRepository.save(
                DeliveryJpaEntity(
                    orderId = 90002L, dispatchId = 1L, carrierId = 1L, laundromatId = 1L,
                    status = DeliveryStatus.PICKUP_PENDING, actualWeight = null,
                ),
            ).id
        }!!
        val stale = tx.execute { deliveryJpaRepository.findById(id).get() }!!
        tx.execute { deliveryJpaRepository.findById(id).get().apply { actualWeight = BigDecimal("1.00") } }
        stale.actualWeight = BigDecimal("2.00")   // 스칼라만 변경 — steps 컬렉션은 절대 건드리지 않음
        assertThatThrownBy { tx.execute { deliveryJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }

    @Test
    fun `같은 Review를 stale 버전으로 저장하면 OptimisticLockingFailureException`() {
        val id = tx.execute {
            reviewJpaRepository.save(
                ReviewJpaEntity(laundromatId = 1L, customerId = 1L, comment = "c", rating = 5),
            ).id
        }!!
        val stale = tx.execute { reviewJpaRepository.findById(id).get() }!!
        tx.execute { reviewJpaRepository.findById(id).get().apply { comment = "first" } }
        stale.comment = "second"
        assertThatThrownBy { tx.execute { reviewJpaRepository.save(stale) } }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }
}
