package com.carry.app.idempotency

import com.carry.app.test.IntegrationTestBase
import com.carry.infra.kafka.consumer.EventConsumerSupport
import com.carry.infra.kafka.consumer.ProcessedEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 멱등성 회귀 — 소비자측 dedup의 실DB/동시성 검증(L1).
 *
 * `EventConsumerSupport.processIfNotDuplicate`를 실제 트랜잭션·실제 PostgreSQL로 구동하여
 * "동일 eventId가 여러 번 들어와도 부수효과는 최대 1회 커밋"임을 증명한다.
 *
 * 관측 가능한 부수효과 = 별도 marker 테이블 insert. 이 insert는 `processIfNotDuplicate`의
 * `@Transactional` 경계 안에서 실행되므로, dedup·실패 시 tx와 함께 롤백된다(트랜잭션 참여 증명).
 * marker는 raw JDBC 테이블(JPA 엔티티 아님)이라 `ddl-auto: validate`와 충돌하지 않는다.
 */
class ConsumerIdempotencyIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var eventConsumerSupport: EventConsumerSupport
    @Autowired lateinit var processedEventRepository: ProcessedEventRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS idem_test_marker " +
                "(seq bigserial primary key, event_id varchar(255) not null)"
        )
        jdbc.update("TRUNCATE idem_test_marker")
        jdbc.update("DELETE FROM processed_events")
    }

    @AfterEach
    fun tearDown() {
        jdbc.update("DELETE FROM processed_events")
        jdbc.execute("DROP TABLE IF EXISTS idem_test_marker")
    }

    /** block: marker 행 1개 insert (관측 가능한 부수효과). */
    private fun insertMarker(eventId: String) {
        jdbc.update("INSERT INTO idem_test_marker(event_id) VALUES (?)", eventId)
    }

    private fun markerCount(eventId: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM idem_test_marker WHERE event_id = ?", Int::class.java, eventId)!!

    private fun processedCount(eventId: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE id = ?", Int::class.java, eventId)!!

    @Test
    fun `같은 이벤트를 순차로 두 번 처리해도 부수효과는 한 번만 커밋된다`() {
        val eventId = "evt-seq-1"

        eventConsumerSupport.processIfNotDuplicate(eventId, eventType = "OrderCreatedEvent") { insertMarker(eventId) }
        eventConsumerSupport.processIfNotDuplicate(eventId, eventType = "OrderCreatedEvent") { insertMarker(eventId) }

        assertThat(markerCount(eventId)).isEqualTo(1)
        assertThat(processedCount(eventId)).isEqualTo(1)
    }

    @Test
    fun `block이 실패하면 marker와 ProcessedEvent가 함께 롤백된다`() {
        val eventId = "evt-fail-1"

        assertThatThrownBy {
            eventConsumerSupport.processIfNotDuplicate(eventId, eventType = "OrderCreatedEvent") {
                insertMarker(eventId)                       // 부수효과 발생 후
                throw IllegalStateException("downstream failure")  // 같은 tx 안에서 실패
            }
        }.isInstanceOf(IllegalStateException::class.java)

        // 트랜잭션 롤백으로 marker·마킹 모두 사라져야 한다 → at-least-once 재처리 가능.
        assertThat(markerCount(eventId)).isEqualTo(0)
        // claim-first: claim INSERT가 block 이전에 일어나므로, 이 0 단언은 claim 행 자체가
        // tx 롤백으로 사라짐을 증명한다(재처리 가능).
        assertThat(processedCount(eventId)).isEqualTo(0)
    }

    /**
     * 진짜 동시 중복 배달에서도 **block(부수효과)이 정확히 1회**만 커밋됨을 증명한다(block-at-most-once).
     *
     * claim-first(`processIfNotDuplicate`가 block 이전에 `ProcessedEventRepository.claim()` =
     * `INSERT ... ON CONFLICT (id) DO NOTHING`으로 선점)이므로, 8스레드가 동시에 같은 eventId를
     * 밀어넣어도 Postgres가 한 트랜잭션만 1행 삽입을 통과시키고 나머지는 0행(skip)을 받는다.
     * 따라서 marker(부수효과)·processed(마킹) 모두 정확히 1, 패자도 예외 없이 종료한다.
     * 실패 롤백·순차 dedup은 위 두 테스트가 보장한다.
     */
    @Test
    fun `같은 이벤트를 8개 스레드가 동시에 처리해도 처리 마킹은 정확히 한 번만 영속된다`() {
        val eventId = "evt-concurrent-1"
        val threads = 8
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)

        val futures = (1..threads).map {
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    eventConsumerSupport.processIfNotDuplicate(eventId, eventType = "OrderCreatedEvent") {
                        insertMarker(eventId)
                    }
                }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        start.countDown()                       // 모든 스레드 동시 진입 → PK race 강제
        val results = futures.map { it.get(20, TimeUnit.SECONDS) }
        executor.shutdown()

        val ok = results.count { it.isSuccess }
        val failed = results.count { it.isFailure }
        val marker = markerCount(eventId)
        val processed = processedCount(eventId)

        // claim-first: 동시 중복에서도 부수효과(block)는 정확히 1회만 커밋된다(block-at-most-once).
        assertThat(marker)
            .`as`("marker=%d processed=%d ok=%d failed=%d", marker, processed, ok, failed)
            .isEqualTo(1)
        // 처리 마킹도 정확히 1행(DB PK + ON CONFLICT가 강제).
        assertThat(processed).isEqualTo(1)
        // 패자 스레드도 예외 없이 깨끗이 skip(0행 claim) → 전부 성공.
        assertThat(ok).isEqualTo(threads)
        assertThat(failed).isEqualTo(0)
    }
}
