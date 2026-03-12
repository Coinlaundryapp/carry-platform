package com.carry.app.saga

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.OutboxEventAssertions
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.dispatch.application.port.inbound.AcceptAssignmentCommand
import com.carry.dispatch.application.port.inbound.AssignDispatchCommand
import com.carry.dispatch.application.port.inbound.ClaimDispatchCommand
import com.carry.dispatch.application.port.inbound.DispatchSagaEventHandler
import com.carry.dispatch.application.port.inbound.RejectAssignmentCommand
import com.carry.dispatch.application.service.DispatchCommandService
import com.carry.dispatch.application.port.outbound.DispatchPersistencePort
import com.carry.dispatch.domain.exception.CarrierNotInAreaException
import com.carry.dispatch.domain.vo.DispatchStatus
import com.carry.event.order.OrderCreatedEvent
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@Import(SagaIntegrationTestConfig::class)
class DispatchSagaIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var dispatchCommandService: DispatchCommandService
    @Autowired lateinit var dispatchSagaHandler: DispatchSagaEventHandler
    @Autowired lateinit var dispatchPersistencePort: DispatchPersistencePort
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var objectMapper: ObjectMapper

    private lateinit var outbox: OutboxEventAssertions

    @BeforeEach
    fun setUp() {
        outbox = OutboxEventAssertions(jdbc, objectMapper)
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertCarrier(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertCarrierArea(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    private fun createOrderAndDispatch(): Pair<Long, Long> {
        val order = orderCommandService.createOrder(
            CreateOrderCommand(
                customerId = TestFixtures.CUSTOMER_ID,
                shippingAddressId = TestFixtures.SHIPPING_ADDRESS_ID,
                laundromatId = TestFixtures.LAUNDROMAT_ID,
                laundryItemType = "NORMAL",
                selectedOptions = listOf(SelectedOptionCommand("WASH", "COLD")),
                desiredPickupAt = TestFixtures.desiredPickupAt(),
                desiredDeliveryAt = TestFixtures.desiredDeliveryAt(),
                areaCode = TestFixtures.AREA_CODE,
            )
        )
        val orderId = order.id!!

        val event = outbox.readOutboxPayload<OrderCreatedEvent>("Order", "OrderCreatedEvent", orderId.toString())
        dispatchSagaHandler.onOrderCreated(event)

        val dispatch = dispatchPersistencePort.findByOrderId(orderId)!!
        return orderId to dispatch.id!!
    }

    @Test
    fun `coordinator assigns dispatch, carrier accepts`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        // 코디네이터가 배차 지정
        val assigned = dispatchCommandService.assignDispatch(
            AssignDispatchCommand(dispatchId, TestFixtures.CARRIER_ID)
        )
        assertThat(assigned.status).isEqualTo(DispatchStatus.ASSIGNED)

        // 캐리어가 수락
        val accepted = dispatchCommandService.acceptAssignment(
            AcceptAssignmentCommand(dispatchId, TestFixtures.CARRIER_ID)
        )
        assertThat(accepted.status).isEqualTo(DispatchStatus.ACCEPTED)
        outbox.assertOutboxContains("Dispatch", "DispatchAcceptedEvent", orderId.toString())
    }

    @Test
    fun `carrier rejects forced assignment -- penalty recorded and dispatch returns to PENDING`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        // 코디네이터가 배차 지정
        dispatchCommandService.assignDispatch(AssignDispatchCommand(dispatchId, TestFixtures.CARRIER_ID))

        // 캐리어가 거절 → 페널티 기록, PENDING 복귀
        val rejected = dispatchCommandService.rejectAssignment(
            RejectAssignmentCommand(dispatchId, TestFixtures.CARRIER_ID)
        )
        assertThat(rejected.status).isEqualTo(DispatchStatus.PENDING)

        // 페널티 기록 확인
        val penaltyCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_penalty_records WHERE carrier_id = ? AND dispatch_id = ?",
            Long::class.java,
            TestFixtures.CARRIER_ID, dispatchId,
        )
        assertThat(penaltyCount).isEqualTo(1L)
    }

    @Test
    fun `carrier not in area cannot claim dispatch`() {
        val (orderId, dispatchId) = createOrderAndDispatch()

        val otherCarrierId = 99L
        TestFixtures.insertCarrier(jdbc, otherCarrierId)
        // otherCarrierId has no CarrierArea registered

        assertThatThrownBy {
            dispatchCommandService.claimDispatch(
                ClaimDispatchCommand(dispatchId, otherCarrierId)
            )
        }.isInstanceOf(CarrierNotInAreaException::class.java)
    }
}
