package com.carry.app.test

import com.carry.payment.application.port.outbound.PgProviderAdapter
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class SagaIntegrationTestConfig {

    @Bean
    @Primary
    fun fakePgProviderAdapter(): PgProviderAdapter = FakePgProviderAdapter()
}
