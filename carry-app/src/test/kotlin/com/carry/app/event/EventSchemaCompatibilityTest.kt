package com.carry.app.event

import com.carry.event.order.OrderCreatedEvent
import com.carry.event.order.SelectedOptionDto
import com.carry.event.order.ShippingAddressDto
import com.carry.infra.kafka.consumer.OutboxEventEnvelope
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Instant

/**
 * ADR-0005 이벤트 스키마 진화 전략의 회귀 가드.
 *
 * 프로덕션 매퍼는 Spring Boot 4 의 [JacksonAutoConfiguration] 이 만드는 Jackson 3 [JsonMapper] 다
 * (커스텀 매퍼 @Bean·spring.jackson.* 설정 없음). 본 테스트는 **같은 자동구성을 [ApplicationContextRunner] 로
 * 띄워 그 빈을 그대로 쓴다** — 미지 필드 무시·ISO-8601 날짜·클래스패스 모듈(kotlin) 등록 같은 기본값을 손으로
 * 흉내 내지 않으므로, Boot 의 기본값이 바뀌면 이 테스트가 먼저 알려 준다.
 * (Boot 3 시절에는 Jackson2ObjectMapperBuilder 로 동급 매퍼를 구성했다.)
 *
 * 이 테스트가 깨지면 가산적 진화(생산자·소비자 독립 배포)의 전제가 무너진 것이다.
 */
class EventSchemaCompatibilityTest {

    private val objectMapper: ObjectMapper = bootJsonMapper()

    private fun bootJsonMapper(): JsonMapper {
        var mapper: JsonMapper? = null
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .run { context -> mapper = context.getBean(JsonMapper::class.java) }
        return checkNotNull(mapper)
    }

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
