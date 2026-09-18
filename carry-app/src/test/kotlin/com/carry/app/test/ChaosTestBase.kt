package com.carry.app.test

import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.ToxiproxyClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.ToxiproxyContainer
import org.testcontainers.utility.DockerImageName

/**
 * 인프라 장애 주입 테스트 전용 베이스.
 *
 * [IntegrationTestBase]와 분리한 이유 — 이쪽은 DataSource 가 **Toxiproxy 를 경유**해야
 * 네트워크를 끊었다 붙였다 할 수 있다. 기존 통합 테스트(914개)는 프록시 없이 컨테이너에
 * 직결하므로 베이스를 공유하면 불필요한 홉과 컨텍스트 분기가 생긴다.
 *
 * HikariCP 설정은 `application-test.yml` 이 덮지 않으므로 **운영과 동일한 값**이 적용된다
 * (`maximum-pool-size=20`, `connection-timeout=3000`, `leak-detection-threshold=5000`).
 * 즉 여기서 관찰되는 거동은 운영 설정의 거동이다.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class ChaosTestBase {

    companion object {
        private val network: Network = Network.newNetwork()

        private val postgres =
            PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"),
            )
                .withDatabaseName("carry_chaos")
                .withUsername("test")
                .withPassword("test")
                .withNetwork(network)
                .withNetworkAliases("pgdb")

        private val toxiproxy =
            ToxiproxyContainer(DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0"))
                .withNetwork(network)

        /** 테스트에서 toxic 을 붙였다 떼는 대상. DB 로 가는 유일한 경로다. */
        lateinit var dbProxy: Proxy
            private set

        private var proxiedPort: Int = 0

        init {
            postgres.start()
            toxiproxy.start()
            val client = ToxiproxyClient(toxiproxy.host, toxiproxy.controlPort)
            // 컨테이너 내부 8666 을 pgdb:5432 로 포워딩한다.
            dbProxy = client.createProxy("carry-pg", "0.0.0.0:8666", "pgdb:5432")
            proxiedPort = toxiproxy.getMappedPort(8666)
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:postgresql://${toxiproxy.host}:$proxiedPort/carry_chaos"
            }
            registry.add("spring.datasource.username") { "test" }
            registry.add("spring.datasource.password") { "test" }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }
}
