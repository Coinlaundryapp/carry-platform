package com.carry.notification.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationTemplateTest {

    @Test
    fun `템플릿에 파라미터를 렌더링할 수 있다`() {
        val template = NotificationTemplate(
            type = NotificationType.ORDER_CREATED,
            channel = NotificationChannel.KAKAO_ALARMTALK,
            titleTemplate = "{customerName}님의 주문",
            contentTemplate = "주문번호 {orderId}번이 접수되었습니다. 예상 수거 시각: {pickupTime}",
        )

        val (title, content) = template.render(
            mapOf(
                "customerName" to "홍길동",
                "orderId" to "123",
                "pickupTime" to "14:00",
            ),
        )

        assertThat(title).isEqualTo("홍길동님의 주문")
        assertThat(content).isEqualTo("주문번호 123번이 접수되었습니다. 예상 수거 시각: 14:00")
    }

    @Test
    fun `파라미터가 없으면 템플릿 그대로 반환한다`() {
        val template = NotificationTemplate(
            type = NotificationType.PAYMENT_COMPLETED,
            channel = NotificationChannel.SMS,
            titleTemplate = "결제 완료",
            contentTemplate = "결제가 완료되었습니다.",
        )

        val (title, content) = template.render(emptyMap())

        assertThat(title).isEqualTo("결제 완료")
        assertThat(content).isEqualTo("결제가 완료되었습니다.")
    }

    @Test
    fun `일부 파라미터만 매칭되면 나머지는 그대로 유지된다`() {
        val template = NotificationTemplate(
            type = NotificationType.DELIVERY_COMPLETED,
            channel = NotificationChannel.KAKAO_ALARMTALK,
            titleTemplate = "{customerName}님",
            contentTemplate = "주문번호 {orderId}번 배달 완료. 평점: {rating}",
        )

        val (title, content) = template.render(
            mapOf(
                "customerName" to "김철수",
                "orderId" to "456",
            ),
        )

        assertThat(title).isEqualTo("김철수님")
        assertThat(content).isEqualTo("주문번호 456번 배달 완료. 평점: {rating}")
    }
}
