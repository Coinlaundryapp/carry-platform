package com.carry.app.event

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import com.carry.app.test.TestFixtures
import com.carry.infra.kafka.outbox.OutboxEvent
import com.carry.infra.kafka.outbox.OutboxEventRepository
import com.carry.order.application.port.inbound.CreateOrderCommand
import com.carry.order.application.port.inbound.SelectedOptionCommand
import com.carry.order.application.service.OrderCommandService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Outbox 발행 원자성 회귀 가드 — ADR-0002(Outbox+CDC)의 dual-write 회피 전제.
 *
 * 비즈니스 변경과 outbox 기록은 같은 트랜잭션이어야 한다. 이 테스트는 그 역방향,
 * 즉 **outbox 기록(producer측 DB 쓰기)이 실패하면 비즈니스 변경도 함께 롤백**됨을 잠근다.
 * 이 불변식이 깨지면(예: 발행을 REQUIRES_NEW로 분리, 예외 삼킴) 주문은 생겼는데
 * 이벤트가 없는 — 사가가 영원히 시작되지 않는 — 유령 주문이 생긴다.
 *
 * 순방향(정상 경로에서 주문과 outbox 행이 함께 커밋)은 [com.carry.app.saga.OrderSagaIntegrationTest]가 커버.
 *
 * `@SpykBean`이 아니라 `@MockkBean`인 이유: JPA 리포지토리는 JDK 동적 프록시라
 * mockk `spyk`의 필드 복사(`InternalPlatform.copyFields`)가 `IllegalAccessException`으로
 * 실패한다. 이 테스트의 유일한 리포지토리 사용처는 save()이므로 전체 모킹으로 충분하다.
 */
@Import(SagaIntegrationTestConfig::class)
class OutboxAtomicityIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var orderCommandService: OrderCommandService
    @Autowired lateinit var jdbc: JdbcTemplate

    @MockkBean(relaxed = true)
    lateinit var outboxEventRepository: OutboxEventRepository

    @BeforeEach
    fun setUp() {
        TestFixtures.insertCustomer(jdbc)
        TestFixtures.insertLaundromat(jdbc)
        TestFixtures.insertShippingAddress(jdbc)
        TestFixtures.insertServiceArea(jdbc)
    }

    @AfterEach
    fun tearDown() {
        TestFixtures.truncateAll(jdbc)
    }

    @Test
    fun `outbox 저장 실패 시 주문 생성도 함께 롤백된다 — 유령 주문 없음`() {
        every { outboxEventRepository.save(any<OutboxEvent>()) } throws
            DataAccessResourceFailureException("simulated outbox DB failure")

        // DataAccessException 단언이 픽스처 유효성도 검증한다 — 픽스처가 깨졌다면
        // outbox 발행에 도달하기 전에 BusinessException이 났을 것이다.
        assertThatThrownBy {
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
        }.isInstanceOf(DataAccessException::class.java)

        val orderCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE customer_id = ?",
            Long::class.java,
            TestFixtures.CUSTOMER_ID,
        )!!
        assertThat(orderCount)
            .withFailMessage("outbox 저장이 실패했는데 주문이 커밋됐다 — 발행이 같은 트랜잭션이 아니다")
            .isEqualTo(0)
    }
}
