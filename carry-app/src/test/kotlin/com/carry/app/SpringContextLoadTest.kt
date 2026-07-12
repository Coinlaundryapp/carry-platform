package com.carry.app

import com.carry.app.test.IntegrationTestBase
import com.carry.app.test.SagaIntegrationTestConfig
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import

/**
 * DI 그래프가 실제로 부팅되는지에 대한 최초의 자동화된 증거.
 *
 * Task 13(BillingQueryPortAdapter 배선)까지는 `carry-app` 컨텍스트가 부팅되는지 확인하는
 * 자동화 테스트가 없었다 — 다른 통합 테스트가 우연히 컨텍스트를 띄웠을 뿐이다. 이 테스트는
 * 그 자체가 목적: 빈 정의 누락·순환 참조·프로퍼티 바인딩 실패 등을 다른 어떤 비즈니스 로직보다
 * 먼저, 가장 싼 값으로 잡아낸다.
 */
@Import(SagaIntegrationTestConfig::class)
class SpringContextLoadTest : IntegrationTestBase() {

    @Test
    fun contextLoads() {
    }
}
