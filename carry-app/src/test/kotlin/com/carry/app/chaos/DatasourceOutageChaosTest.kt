package com.carry.app.chaos

import com.carry.app.test.ChaosTestBase
import com.zaxxer.hikari.HikariDataSource
import eu.rekawek.toxiproxy.model.ToxicDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

/**
 * DB 경로 단절/지연 주입 — "쿼리 실패가 커넥션 생명주기로 번지지 않는다"를 확인한다.
 *
 * 검증 대상은 성능이 아니라 **거동**이다. 절대 처리량·지연은 측정하지 않으며,
 * 여기서 단언하는 것은 세 가지뿐이다.
 *  1. 단절 시 설정된 커넥션 타임아웃 안에서 실패한다(무한 대기하지 않는다)
 *  2. 단절이 해소되면 **사람의 개입 없이** 회복한다
 *  3. 단절·회복을 겪어도 풀이 상한(20)을 넘겨 팽창하지 않는다
 *
 * 3번이 이 테스트의 핵심이다. 쿼리 실패를 커넥션 문제로 오인해 풀을 재생성하는 구조는
 * 실패가 반복될 때 커넥션·스레드가 선형으로 증가해 프로세스를 죽인다.
 *
 * 실측 2026-09-04: 이 테스트를 처음 붙였을 때 1번이 **13,424,276ms(3시간 43분)** 동안
 * 반환되지 않았다(RED). `connection-timeout` 은 커넥션 '획득'에만 적용되고 소켓 읽기에는
 * 적용되지 않으며, pgjdbc `socketTimeout` 기본값이 0(무한)이었기 때문이다.
 * `data-source-properties.socketTimeout=10` 적용 후 **11.2초**에 실패(GREEN).
 */
class DatasourceOutageChaosTest : ChaosTestBase() {

    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var jdbc: JdbcTemplate

    private val hikari: HikariDataSource
        get() = dataSource.unwrap(HikariDataSource::class.java)

    @AfterEach
    fun healNetwork() {
        dbProxy.toxics().getAll().forEach { toxic -> toxic.remove() }
    }

    private fun cutNetwork() {
        // timeout(0) = 연결을 끊지 않고 데이터만 흘리지 않는다(블랙홀).
        // connection-refused 로 즉시 실패시키면 커넥션 타임아웃 경로를 태울 수 없다.
        dbProxy.toxics().timeout("cut-down", ToxicDirection.DOWNSTREAM, 0)
        dbProxy.toxics().timeout("cut-up", ToxicDirection.UPSTREAM, 0)
    }

    @Test
    @Timeout(60) // 회귀 시에도 스위트를 잡아먹지 않도록 상한을 건다
    fun `DB 경로가 끊기면 소켓 타임아웃(10초) 안에서 실패한다 - 무한 대기하지 않는다`() {
        assertThat(jdbc.queryForObject("SELECT 1", Int::class.java)).isEqualTo(1)

        cutNetwork()

        var thrown: Throwable? = null
        val elapsed = measureTimeMillis {
            thrown = runCatching { jdbc.queryForObject("SELECT 1", Int::class.java) }.exceptionOrNull()
        }

        assertThat(thrown).isNotNull()
        // socketTimeout=10초 + 드라이버·프록시 오버헤드. 설정이 빠지면 무한 대기라 여기서 걸린다.
        assertThat(elapsed).isLessThan(30_000L)
    }

    @Test
    @Timeout(60)
    fun `단절 후 복구되면 사람 개입 없이 회복하고 풀은 상한을 넘지 않는다`() {
        val maxPoolSize = hikari.maximumPoolSize
        assertThat(maxPoolSize).isEqualTo(20) // 운영 설정이 그대로 적용됐는지 확인

        cutNetwork()
        // 실패를 여러 번 반복시킨다 — 실패 1건이 커넥션 폐기·재생성을 유발한다면
        // 반복 횟수에 비례해 커넥션이 증식하는지 여기서 드러난다.
        repeat(5) {
            runCatching { jdbc.queryForObject("SELECT 1", Int::class.java) }
        }

        healNetwork()

        // 회복은 재기동이나 풀 재생성 없이 이루어져야 한다.
        val recovered = retryUntilSuccess(attempts = 20, intervalMs = 500) {
            jdbc.queryForObject("SELECT 1", Int::class.java)
        }
        assertThat(recovered).isEqualTo(1)

        val pool = hikari.hikariPoolMXBean
        assertThat(pool.totalConnections).isLessThanOrEqualTo(maxPoolSize)
        assertThat(pool.activeConnections).isLessThanOrEqualTo(maxPoolSize)
    }

    private fun <T> retryUntilSuccess(attempts: Int, intervalMs: Long, block: () -> T): T {
        var last: Throwable? = null
        repeat(attempts) {
            runCatching(block).onSuccess { return it }.onFailure { last = it }
            Thread.sleep(intervalMs)
        }
        throw AssertionError("복구되지 않았다 (${attempts}회 시도)", last)
    }
}
