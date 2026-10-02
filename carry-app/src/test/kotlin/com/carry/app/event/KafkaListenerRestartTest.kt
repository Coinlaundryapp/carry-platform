package com.carry.app.event

import com.carry.app.test.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.kafka.config.KafkaListenerEndpointRegistry

/**
 * 컨텍스트가 재시작(Lifecycle stop → start)돼도 `auto-startup=false` 리스너는 시작하지 않아야 한다.
 *
 * Spring Kafka 레지스트리의 기본값(alwaysStartAfterRefresh=true)은 refresh 이후의 start() 에서 컨테이너의
 * autoStartup 을 무시하고 전부 시작한다. Spring Framework 7 의 테스트 컨텍스트 캐시는 쓰지 않는 컨텍스트를
 * 일시정지했다가 재사용할 때 restart 하므로, 브로커가 없는 테스트 프로필에서 리스너 컨슈머 수십 개가 매번
 * 떴다 닫혔다. 그 churn 이 JDK 21 가상 스레드 pinning 교착(AppInfoParser 모니터 + Logback 락)으로
 * 4코어 CI 의 carry-app 테스트를 멈췄다(Spring Boot 4 이관, #210).
 */
class KafkaListenerRestartTest : IntegrationTestBase() {

    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var registry: KafkaListenerEndpointRegistry

    @Test
    fun `컨텍스트가 재시작돼도 auto-startup=false 리스너는 시작하지 않는다`() {
        assertThat(registry.listenerContainers).isNotEmpty
        assertThat(registry.listenerContainers).allSatisfy { assertThat(it.isAutoStartup).isFalse() }

        val configurable = context as ConfigurableApplicationContext
        configurable.stop()
        configurable.start()

        assertThat(registry.listenerContainers).allSatisfy { assertThat(it.isRunning).isFalse() }
    }
}
