package com.carry.app.event

import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.SelectedOptionDto
import com.carry.event.order.ShippingAddressDto
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.Instant

/**
 * ADR-0005 이벤트 스키마 진화 전략의 회귀 가드.
 *
 * 프로덕션 ObjectMapper는 Spring Boot 자동설정이 [Jackson2ObjectMapperBuilder]로 만든다
 * (커스텀 ObjectMapper @Bean·spring.jackson.* 설정 없음). 본 테스트는 같은 빌더로 동급 매퍼를
 * 구성해 tolerant-reader 불변식을 잠근다 — 이 빌더는 기본적으로 FAIL_ON_UNKNOWN_PROPERTIES 와
 * WRITE_DATES_AS_TIMESTAMPS 를 비활성화하고 클래스패스의 well-known 모듈(kotlin·jsr310)을 등록한다.
 *
 * 이 테스트가 깨지면 가산적 진화(생산자·소비자 독립 배포)의 전제가 무너진 것이다.
 */
class EventSchemaCompatibilityTest {

    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Test
    fun `전방 호환 — 생산자가 추가한 미지의 필드를 구버전 소비자가 무시한다`() {
        // 미래 생산자가 OrderCreatedEvent(및 중첩 DTO)에 새 필드를 추가한 상황을 모사.
        val futureJson = """
            {
              "orderId": 1, "customerId": 2, "laundromatId": 3,
              "laundryItemType": "DRY_CLEANING",
              "selectedOptions": [{"optionType":"WASH","subOptionType":"STANDARD","newSubField":"x"}],
              "shippingAddress": {
                "roadAddress":"서울시 강남구","detailAddress":"101호",
                "latitude":37.5,"longitude":127.0,"recipientName":"홍길동","recipientPhone":"01000000000",
                "newAddressField":"ignored"
              },
              "desiredPickupAt": "2026-06-10T00:00:00Z",
              "desiredDeliveryAt": "2026-06-11T00:00:00Z",
              "areaCode": "GANGNAM",
              "newFutureField": "ignored-by-old-consumer"
            }
        """.trimIndent()

        val event = objectMapper.readValue(futureJson, OrderCreatedEvent::class.java)

        assertThat(event.orderId).isEqualTo(1L)
        assertThat(event.areaCode).isEqualTo("GANGNAM")
        assertThat(event.selectedOptions).singleElement()
            .satisfies({ assertThat(it.optionType).isEqualTo("WASH") })
    }

    @Test
    fun `후방 호환 — 엔벌로프의 선택 필드(traceId, createdAt) 누락도 역직렬화된다`() {
        // 과거 생산자가 traceId/createdAt 없이 발행한 엔벌로프.
        val legacyEnvelope =
            """{"id":"evt-1","aggregateType":"Order","aggregateId":"1","eventType":"OrderCreatedEvent","payload":"{}"}"""

        val envelope = objectMapper.readValue(legacyEnvelope, OutboxEventEnvelope::class.java)

        assertThat(envelope.id).isEqualTo("evt-1")
        assertThat(envelope.eventType).isEqualTo("OrderCreatedEvent")
        assertThat(envelope.traceId).isNull()
        assertThat(envelope.createdAt).isNull()
    }

    @Test
    fun `round-trip — 직렬화 후 역직렬화가 원본과 동등하다`() {
        val event = OrderCreatedEvent(
            orderId = 1, customerId = 2, laundromatId = 3,
            laundryItemType = "DRY_CLEANING",
            selectedOptions = listOf(SelectedOptionDto("WASH", "STANDARD")),
            shippingAddress = ShippingAddressDto(
                roadAddress = "서울시 강남구", detailAddress = "101호",
                latitude = 37.5, longitude = 127.0,
                recipientName = "홍길동", recipientPhone = "01000000000",
            ),
            desiredPickupAt = Instant.parse("2026-06-10T00:00:00Z"),
            desiredDeliveryAt = Instant.parse("2026-06-11T00:00:00Z"),
            areaCode = "GANGNAM",
        )

        val json = objectMapper.writeValueAsString(event)
        val back = objectMapper.readValue(json, OrderCreatedEvent::class.java)

        assertThat(back).isEqualTo(event)
    }
}
