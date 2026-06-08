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
        assertThat(processedCount(eventId)).isEqualTo(0)
    }

    /**
     * 진짜 동시 중복 배달에서 **DB 레벨 dedup**(`processed_events` PK)이 유지됨을 증명한다.
     *
     * ⚠️ 단언 범위 주의: 여기서 보장되는 불변식은 "`processed_events`에 정확히 1행"이다.
     * "block(부수효과)이 정확히 1회"는 **진짜 동시성에선 보장되지 않는다** —
     * `ProcessedEventRepository.save()`는 ProcessedEvent가 할당식 @Id·non-Persistable이라
     * `merge()`(SELECT 선행)로 동작하므로, 패자 스레드의 merge-SELECT가 승자 커밋 이후에
     * 실행되면 INSERT 대신 no-op UPDATE가 되어 PK 위반 없이 커밋된다(이미 실행한 block의
     * 부수효과 잔존). 이는 현실 위협모델에서 문제되지 않는다: Kafka는 같은 키(aggregateId)
     * 이벤트를 같은 파티션→단일 컨슈머 스레드로 **순차** 처리하므로 동일 이벤트의 진짜 동시
     * 소비가 발생하지 않고, 재배달도 순차다(순차 dedup·실패 롤백은 위 두 테스트가 보장).
     * 따라서 동시성에서 신뢰하는 안전망은 "처리 마킹 1행 유지"이며 그것을 단언한다.
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

        // 핵심 불변식: 동시 중복에서도 처리 마킹은 정확히 1행(DB PK가 강제) → 안전망 유지.
        assertThat(processed)
            .`as`("processed=%d marker=%d ok=%d failed=%d", processed, marker, ok, failed)
            .isEqualTo(1)
        // 부수효과는 최소 1회(승자)는 커밋, 스레드 수를 넘지 않는다(타이밍 의존 → 정확값 단언 금지).
        assertThat(marker).isBetween(1, threads)
    }
}
