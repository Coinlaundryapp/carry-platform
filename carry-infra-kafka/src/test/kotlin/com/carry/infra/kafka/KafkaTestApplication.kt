package com.carry.infra.kafka

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration

/**
 * carry-infra-kafka 통합 테스트용 최소 Spring Boot 컨피그.
 *
 * 본 모듈은 OutboxEvent/ProcessedEvent JPA 엔티티를 포함하지만, 통합 테스트는
 * Kafka 인프라(retry/DLQ)만 검증하므로 DataSource·JPA 자동 구성을 비활성화한다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataJpaRepositoriesAutoConfiguration::class,
    ],
)
open class KafkaTestApplication
