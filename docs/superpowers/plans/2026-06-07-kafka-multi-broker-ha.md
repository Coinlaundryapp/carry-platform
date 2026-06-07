# Kafka 멀티브로커 HA Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kafka 단일 브로커(SPOF)를 3-node 겸임 KRaft 클러스터로 올려, 브로커 1대 다운에도 이벤트 무손실(RF=3·min-ISR=2·acks=all)을 보장하고 자동 IT + 라이브 스모크로 회귀 방지한다.

**Architecture:** 복제 계수/최소 ISR을 `carry.kafka.*` 프로퍼티로 외부화(데이터클래스 기본 1/1 → base/test 무영향, local 3/2, dev/prod env). `KafkaTopicConfig`가 주입값으로 DLQ 토픽을 선언하고, Debezium 이벤트 토픽·connect 내부 토픽·브로커 내부 토픽도 RF=3으로 정합. docker-compose는 리스너 3종 분리(CONTROLLER 내부전용 / INTERNAL 브로커간 / EXTERNAL 호스트)로 advertised footgun을 차단. 검증은 Testcontainers 3브로커(고정포트) 무손실 IT + 라이브 풀스택 브로커 kill 스모크.

**Tech Stack:** Kotlin, Spring Boot 3.4.1, Spring Kafka, apache/kafka:3.8.1 (KRaft), Debezium 2.7, Testcontainers 1.20.4, JUnit5, AssertJ, Awaitility.

**Spec:** `docs/superpowers/specs/2026-06-07-kafka-multi-broker-ha-design.md`

**Branch:** `feature/kafka-multi-broker-ha` (base `origin/develop`, 이미 생성됨).

**검증 prefix (전 Gradle 호출):** `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7"`

---

## 파일 구조 (생성/수정)

- **수정** `carry-infra-kafka/.../KafkaTopicConfig.kt` — companion 상수 RF 제거, `KafkaTopicProperties` 주입, `dlqTopics()` 정적→인스턴스 + min.insync.replicas 부여. 신규 `KafkaTopicProperties` 데이터클래스 동거(같은 책임=토픽 복제 정책).
- **수정** `carry-app/.../CarryApplication.kt` — `@EnableConfigurationProperties`에 `KafkaTopicProperties` 추가.
- **수정** `carry-infra-kafka/.../KafkaTopicConfigTest.kt` — 정적→인스턴스 API, 주입 RF/min-ISR 단언.
- **수정** `carry-app/src/main/resources/application-local.yml` — `carry.kafka` 3/2.
- **수정** `carry-app/src/main/resources/application-dev.yml`·`application-prod.yml` — `carry.kafka` env(`:1`).
- **수정** `docker-compose.yml` — `kafka` 1개 → `kafka-1/2/3` + kafka-connect bootstrap/storage RF.
- **수정** `infra/debezium/register-connector.json` — 이벤트 토픽 RF=3, min-ISR=2.
- **수정** `carry-infra-kafka/.../OutboxConnectorContractTest.kt` — RF=3/min-ISR 회귀 단언 추가.
- **수정** `carry-infra-kafka/build.gradle.kts` — Testcontainers 의존 추가.
- **생성** `carry-infra-kafka/.../MultiBrokerNoLossIntegrationTest.kt` — 3브로커 무손실 IT(`@Tag("kafka-cluster")`).

---

## Chunk 1: 설정 외부화 + KafkaTopicConfig 주입 (단위 TDD)

### Task 1: `KafkaTopicProperties` 도입 + `KafkaTopicConfig` 주입 리팩터

**Files:**
- Modify: `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/KafkaTopicConfig.kt`
- Modify: `carry-app/src/main/kotlin/com/carry/app/CarryApplication.kt`
- Test: `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/KafkaTopicConfigTest.kt`

- [ ] **Step 1: 실패 테스트 작성** — 기존 `KafkaTopicConfigTest.kt` 전체를 아래로 교체(정적→인스턴스 + 주입 단언).

```kotlin
package com.carry.infra.kafka

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaTopicConfigTest {

    private fun config(props: KafkaTopicProperties) = KafkaTopicConfig(props)

    @Test
    fun `DLQ 토픽은 소비되는 이벤트 토픽마다 _DLQ 접미사로 선언된다`() {
        val names = config(KafkaTopicProperties()).dlqTopics().map { it.name() }

        assertThat(names).containsExactlyInAnyOrder(
            "carry.Order.events.DLQ",
            "carry.Payment.events.DLQ",
            "carry.Dispatch.events.DLQ",
            "carry.Delivery.events.DLQ",
        )
    }

    @Test
    fun `DLQ 토픽 파티션 수는 소스와 정합하도록 PARTITIONS(6)로 선언된다`() {
        assertThat(KafkaTopicConfig.PARTITIONS).isEqualTo(6)
        assertThat(config(KafkaTopicProperties()).dlqTopics()).allSatisfy {
            assertThat(it.numPartitions()).isEqualTo(6)
        }
    }

    @Test
    fun `기본값은 단일 브로커 호환 - RF 1, min-insync-replicas 1`() {
        // base/test 환경(데이터클래스 기본값) = 기존 단일 브로커 동작 보존.
        assertThat(config(KafkaTopicProperties()).dlqTopics()).allSatisfy {
            assertThat(it.replicationFactor()).isEqualTo(1.toShort())
            assertThat(it.configs()).containsEntry("min.insync.replicas", "1")
        }
    }

    @Test
    fun `주입된 RF와 min-insync-replicas가 모든 DLQ 토픽에 반영된다 - HA 설정`() {
        val ha = config(KafkaTopicProperties(replicationFactor = 3, minInsyncReplicas = 2))
        assertThat(ha.dlqTopics()).allSatisfy {
            assertThat(it.replicationFactor()).isEqualTo(3.toShort())
            assertThat(it.configs()).containsEntry("min.insync.replicas", "2")
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:compileTestKotlin`
Expected: FAIL — `KafkaTopicProperties` 미해결, `KafkaTopicConfig` 생성자 인자 없음.

- [ ] **Step 3: 구현** — `KafkaTopicConfig.kt` 전체를 아래로 교체.

```kotlin
package com.carry.infra.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

/**
 * Kafka 토픽 복제 정책(환경별 기변값).
 *
 * - 단일 브로커(base/test/클라우드 미준비 dev·prod): 기본값 1/1.
 * - 멀티브로커 HA(local, 클라우드 준비 후 dev·prod): 3/2 → 브로커 1대 다운 허용(무손실).
 *
 * 프로듀서는 이미 `acks=all`이라 RF/min-ISR만 올리면 무손실이 성립한다.
 */
@ConfigurationProperties(prefix = "carry.kafka")
data class KafkaTopicProperties(
    /** 토픽 복제 계수. 단일 브로커=1, 멀티브로커 HA=3. */
    val replicationFactor: Short = 1,
    /** acks=all 충족에 필요한 최소 ISR. RF=3 HA에서 2(=1대 다운 허용). */
    val minInsyncReplicas: Int = 1,
)

/**
 * Kafka 토픽 선언 정책.
 *
 * 소유권 분리:
 *   - 이벤트 토픽(`carry.*.events`)은 **Debezium(producer)이 소유**한다.
 *     파티션/복제 계수는 `infra/debezium/register-connector.json`의 `topic.creation.*`에서 설정한다.
 *   - DLQ 토픽(`carry.*.events.DLQ`)은 **앱이 소유**한다. 앱의
 *     [DeadLetterPublishingRecoverer]가 발행하는 producer이므로 앱이 토픽을 선언한다.
 *
 * DLQ를 앱이 명시 선언하는 이유:
 *   [KafkaConfig.dlqDestinationResolver]가 원본 record와 **같은 파티션 번호**로 DLQ를
 *   라우팅한다. 따라서 DLQ 토픽은 소스 이벤트 토픽과 동일한 파티션 수를 가져야 한다.
 *
 * 복제 계수·min.insync.replicas는 [KafkaTopicProperties]로 환경별 주입한다(멀티브로커 HA 갭).
 * ⚠️ [PARTITIONS]와 register-connector.json의 `topic.creation.default.partitions`는
 *    함께 바꿔야 한다(둘 다 6). RF는 register-connector.json `topic.creation.default.replication.factor`와
 *    같은 정책을 공유한다(이벤트 토픽은 Debezium이, DLQ는 여기서).
 */
@Configuration
class KafkaTopicConfig(
    private val properties: KafkaTopicProperties,
) {

    companion object {
        /** 이벤트/DLQ 토픽 파티션 수. register-connector.json의 topic.creation 값과 일치해야 한다. */
        const val PARTITIONS = 6

        /** DLQ를 갖는(=소비되는) 이벤트 토픽 목록. 각 토픽의 `.DLQ`를 앱이 선언한다. */
        val CONSUMED_EVENT_TOPICS: List<String> = listOf(
            "carry.Order.events",
            "carry.Payment.events",
            "carry.Dispatch.events",
            "carry.Delivery.events",
        )
    }

    /**
     * 소비되는 이벤트 토픽마다 대응하는 `.DLQ` 토픽을 소스와 동일한 파티션 수·주입 복제 계수로 만든다.
     * min.insync.replicas는 토픽 단위 설정으로 부여한다.
     */
    fun dlqTopics(): List<NewTopic> =
        CONSUMED_EVENT_TOPICS.map { source ->
            TopicBuilder.name(source + KafkaConfig.DLQ_SUFFIX)
                .partitions(PARTITIONS)
                .replicas(properties.replicationFactor.toInt())
                .config("min.insync.replicas", properties.minInsyncReplicas.toString())
                .build()
        }

    /**
     * DLQ 토픽을 선언한다. [KafkaAdmin]이 기동 시 브로커에 없으면 생성한다(있으면 그대로 둠).
     */
    @Bean
    fun carryDlqTopics(): KafkaAdmin.NewTopics =
        KafkaAdmin.NewTopics(*dlqTopics().toTypedArray())
}
```

- [ ] **Step 4: 프로퍼티 빈 등록** — `CarryApplication.kt`에 `KafkaTopicProperties` 추가.

```kotlin
package com.carry.app

import com.carry.geo.adapter.outbound.external.naver.NaverApiProperties
import com.carry.infra.kafka.KafkaTopicProperties
import com.carry.security.jwt.JwtProperties
import com.carry.user.adapter.outbound.auth.KakaoProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["com.carry"]
)
@EnableConfigurationProperties(
    JwtProperties::class,
    NaverApiProperties::class,
    KakaoProperties::class,
    KafkaTopicProperties::class,
)
class CarryApplication

fun main(args: Array<String>) {
    runApplication<CarryApplication>(*args)
}
```

- [ ] **Step 5: 단위 테스트 통과 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test`
Expected: PASS (KafkaTopicConfigTest 4건 + 기존 인접 테스트 무영향).

- [ ] **Step 6: 커밋**

```bash
git add carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/KafkaTopicConfig.kt \
        carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/KafkaTopicConfigTest.kt \
        carry-app/src/main/kotlin/com/carry/app/CarryApplication.kt
git commit -m "refactor(kafka): 토픽 복제 계수·min.insync.replicas 프로퍼티 외부화

KafkaTopicProperties(carry.kafka.*) 도입 — 기본 1/1(단일 브로커 호환), 멀티브로커 HA는 3/2.
KafkaTopicConfig.dlqTopics() 정적→인스턴스, 주입 RF + min.insync.replicas 부여.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 2: 환경별 yml 설정

**Files:**
- Modify: `carry-app/src/main/resources/application-local.yml`
- Modify: `carry-app/src/main/resources/application-dev.yml`
- Modify: `carry-app/src/main/resources/application-prod.yml`

- [ ] **Step 1: local 3/2** — `application-local.yml`의 `carry:` 블록에 `kafka` 추가. 동시에 `spring.kafka.bootstrap-servers`를 3브로커로.

`spring.kafka` 블록(local):
```yaml
  kafka:
    bootstrap-servers: localhost:9092,localhost:9094,localhost:9096
```
`carry:` 블록(local, 기존 `geo:` 형제로 추가):
```yaml
carry:
  kafka:
    replication-factor: 3
    min-insync-replicas: 2
  geo:
    naver:
      ...
```

- [ ] **Step 2: dev/prod env** — `application-dev.yml`·`application-prod.yml`에 `carry.kafka` env 추가(클라우드 미준비 → 기본 1).

```yaml
carry:
  kafka:
    replication-factor: ${CARRY_KAFKA_REPLICATION_FACTOR:1}
    min-insync-replicas: ${CARRY_KAFKA_MIN_INSYNC_REPLICAS:1}
```
> dev/prod에 기존 `carry:` 블록이 있으면 `kafka:` 키만 병합, 없으면 블록 신설. 들여쓰기 검증.

- [ ] **Step 3: yml 파싱 검증(부팅 없이)**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-app:compileKotlin`
Expected: PASS (yml 문법 오류는 부팅 시 드러나므로 여기선 컴파일만; 부팅 검증은 Chunk 4 라이브 스모크).

- [ ] **Step 4: 커밋**

```bash
git add carry-app/src/main/resources/application-local.yml \
        carry-app/src/main/resources/application-dev.yml \
        carry-app/src/main/resources/application-prod.yml
git commit -m "config(kafka): 환경별 carry.kafka 복제 설정 + local 3브로커 bootstrap

local=3/2, dev·prod=env(:1, 클라우드 준비 후 3/2). local bootstrap을 3브로커로.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 2: docker-compose 3브로커 + Debezium

### Task 3: docker-compose 3브로커 + kafka-connect 재배선

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: `kafka` 서비스 1개 → `kafka-1/2/3`로 교체.** 기존 `kafka:` 블록을 아래 3개로 대체.

```yaml
  kafka-1:
    image: apache/kafka:3.8.1
    container_name: carry-kafka-1
    ports:
      - "9092:9092"   # EXTERNAL (호스트→브로커1)
    environment: &kafka-common-env
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      CLUSTER_ID: carry-kafka-cluster-001
      KAFKA_NODE_ID: 1
      KAFKA_LISTENERS: CONTROLLER://0.0.0.0:9093,INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka-1:19092,EXTERNAL://localhost:9092

  kafka-2:
    image: apache/kafka:3.8.1
    container_name: carry-kafka-2
    ports:
      - "9094:9092"   # 호스트 9094 → 컨테이너 EXTERNAL 9092
    environment:
      <<: *kafka-common-env
      KAFKA_NODE_ID: 2
      KAFKA_LISTENERS: CONTROLLER://0.0.0.0:9093,INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka-2:19092,EXTERNAL://localhost:9094

  kafka-3:
    image: apache/kafka:3.8.1
    container_name: carry-kafka-3
    ports:
      - "9096:9092"   # 호스트 9096 → 컨테이너 EXTERNAL 9092
    environment:
      <<: *kafka-common-env
      KAFKA_NODE_ID: 3
      KAFKA_LISTENERS: CONTROLLER://0.0.0.0:9093,INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka-3:19092,EXTERNAL://localhost:9096
```
> YAML 앵커(`&kafka-common-env` / `<<:`)로 공통 env 공유. `KAFKA_NODE_ID`·`KAFKA_LISTENERS`·`KAFKA_ADVERTISED_LISTENERS`는 브로커별 오버라이드. CONTROLLER(9093)·INTERNAL(19092)는 호스트로 매핑하지 않는다(컨테이너 내부 전용).

- [ ] **Step 2: kafka-connect 재배선** — `kafka-connect` 서비스의 `BOOTSTRAP_SERVERS`·storage RF·`depends_on` 갱신.

```yaml
  kafka-connect:
    image: debezium/connect:2.7
    container_name: carry-kafka-connect
    ports:
      - "8083:8083"
    environment:
      GROUP_ID: carry-connect
      CONFIG_STORAGE_TOPIC: connect-configs
      OFFSET_STORAGE_TOPIC: connect-offsets
      STATUS_STORAGE_TOPIC: connect-status
      BOOTSTRAP_SERVERS: kafka-1:19092,kafka-2:19092,kafka-3:19092
      CONFIG_STORAGE_REPLICATION_FACTOR: 3
      OFFSET_STORAGE_REPLICATION_FACTOR: 3
      STATUS_STORAGE_REPLICATION_FACTOR: 3
    depends_on:
      - kafka-1
      - kafka-2
      - kafka-3
      - postgres
```

- [ ] **Step 3: compose 문법 검증**

Run: `docker compose -f "/c/Users/Eisen/Desktop/Labs/[projects] carry/carry-platform/docker-compose.yml" config > /dev/null && echo OK`
Expected: `OK` (앵커 전개·들여쓰기 정상). 실패 시 YAML 수정.

- [ ] **Step 4: 커밋**

```bash
git add docker-compose.yml
git commit -m "infra(kafka): docker-compose 단일 브로커 → 3-node 겸임 KRaft 클러스터

리스너 3종 분리(CONTROLLER 내부전용/INTERNAL kafka-N:19092/EXTERNAL localhost:9092·9094·9096).
RF=3·min-ISR=2 클러스터 기본값 + offsets/transaction 토픽 RF=3. kafka-connect bootstrap을
3브로커 INTERNAL로, connect storage RF 1→3.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 4: Debezium 커넥터 RF=3 + 계약 회귀 가드

**Files:**
- Modify: `infra/debezium/register-connector.json`
- Test: `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/OutboxConnectorContractTest.kt`

- [ ] **Step 1: 실패 테스트 추가** — `OutboxConnectorContractTest.kt`에 RF/min-ISR 회귀 단언 2건 추가(클래스 끝, 마지막 `}` 앞).

```kotlin
    @Test
    fun `이벤트 토픽 복제 계수는 3이다 - 멀티브로커 HA 회귀 방지`() {
        // RF가 1로 되돌아가면 브로커 1대 다운에 이벤트 토픽이 소실된다. 절대 1로 내리지 말 것.
        val config = connectorConfig()
        assertThat(config["topic.creation.default.replication.factor"]).isEqualTo("3")
    }

    @Test
    fun `이벤트 토픽 min insync replicas는 2다 - acks=all 무손실 보장`() {
        val config = connectorConfig()
        assertThat(config["topic.creation.default.min.insync.replicas"]).isEqualTo("2")
    }
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.OutboxConnectorContractTest"`
Expected: FAIL — 현재 `replication.factor`=1, `min.insync.replicas` 키 부재.

- [ ] **Step 3: 커넥터 설정 수정** — `register-connector.json`의 `config`에서:
  - `"topic.creation.default.replication.factor": "1"` → `"3"`
  - `"topic.creation.default.partitions": "6"` 바로 아래에 `"topic.creation.default.min.insync.replicas": "2",` 추가.

- [ ] **Step 4: 통과 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.OutboxConnectorContractTest"`
Expected: PASS (기존 4건 + 신규 2건).

- [ ] **Step 5: 커밋**

```bash
git add infra/debezium/register-connector.json \
        carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/OutboxConnectorContractTest.kt
git commit -m "infra(kafka): Debezium 이벤트 토픽 RF=3·min-ISR=2 + 계약 회귀 가드

topic.creation.default.replication.factor 1→3, min.insync.replicas 2 추가.
OutboxConnectorContractTest에 RF/min-ISR 회귀 단언 2건 추가.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 3: 자동 IT — Testcontainers 3브로커 무손실

### Task 5: Testcontainers 의존 추가

**Files:**
- Modify: `carry-infra-kafka/build.gradle.kts`

- [ ] **Step 1: testImplementation 추가** — `dependencies` 블록의 test 섹션에:

```kotlin
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.apache.kafka:kafka-clients:3.8.1")
```
> `kafka-clients`는 `spring-kafka`가 이미 추이로 가져오나, IT에서 `AdminClient`/`KafkaProducer`/`KafkaConsumer`를 직접 쓰므로 명시. GenericContainer는 testcontainers core에 포함(별도 kafka 모듈 불필요 — 멀티브로커는 수동 와이어링).

- [ ] **Step 2: 의존 해석 확인**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:dependencies --configuration testRuntimeClasspath > /dev/null && echo OK`
Expected: `OK`.

- [ ] **Step 3: 커밋**

```bash
git add carry-infra-kafka/build.gradle.kts
git commit -m "test(kafka): carry-infra-kafka에 Testcontainers 의존 추가 (멀티브로커 IT용)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 6: 3브로커 무손실 IT

**Files:**
- Create: `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/MultiBrokerNoLossIntegrationTest.kt`

**설계 노트(실행 시 유의):**
- 3개 `GenericContainer`(apache/kafka:3.8.1)를 **공유 `Network`** 위에 KRaft 겸임으로 띄운다. 네트워크 별칭 `kafka-1/2/3`, INTERNAL은 별칭 advertise, EXTERNAL은 **고정 호스트 포트**(`addFixedExposedPort`)로 advertise → Testcontainers 동적 포트 advertised 재배선 문제를 회피(스펙의 최대 리스크 정면 차단). 고정 포트는 docker-compose와 동일(9092/9094/9096) — IT와 라이브 스모크는 동시 실행하지 않으므로 충돌 없음.
- KRaft 겸임은 controller 쿼럼(2/3)이 서야 broker가 "started" 로그를 낸다 → 컨테이너를 **`Startables.deepStart(...)`로 병렬 기동**해야 데드락이 없다.
- `@Tag("kafka-cluster")` — Docker 없는 환경/CI 분리 가능. `@EnabledIfSystemProperty` 등으로 게이트하지 않고, Docker 있으면 항상 도는 IT로 둔다(라이브 게이트는 스펙 폴백 기준으로 판정).
- **폴백 기준(스펙)**: 동일 IT 연속 10회 실행 시 인프라 사유(advertised/포트/타임아웃) 2회 이상 실패 → docker-compose 3브로커 스택 대상 외부기동 태그드 테스트로 전환. Task 6 구현 후 Step 4에서 판정.

- [ ] **Step 1: 무손실 테스트 작성**

```kotlin
package com.carry.infra.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties

/**
 * 멀티브로커 HA 무손실 검증.
 *
 * RF=3·min.insync.replicas=2·acks=all 토픽에 발행 → 브로커 1대 중단 → 추가 발행 + 전량 소비.
 * 단언: (1) 발행 전건이 무손실 소비, (2) 키별 순서 보존.
 *
 * 고정 호스트 포트(9092/9094/9096)로 EXTERNAL을 advertise해 Testcontainers 동적 포트
 * 재배선을 회피한다. KRaft 겸임 쿼럼 데드락 방지를 위해 deepStart로 병렬 기동한다.
 */
@Tag("kafka-cluster")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiBrokerNoLossIntegrationTest {

    private val network: Network = Network.newNetwork()
    private val hostPorts = listOf(9092, 9094, 9096)
    private lateinit var brokers: List<GenericContainer<*>>

    private fun broker(nodeId: Int, hostPort: Int): GenericContainer<*> {
        val c = object : GenericContainer<Nothing>(DockerImageName.parse("apache/kafka:3.8.1")) {}
        c.withNetwork(network)
        c.withNetworkAliases("kafka-$nodeId")
        c.addFixedExposedPort(hostPort, 9092) // host:hostPort → container EXTERNAL 9092
        c.withEnv(
            mapOf(
                "KAFKA_NODE_ID" to "$nodeId",
                "KAFKA_PROCESS_ROLES" to "broker,controller",
                "KAFKA_CONTROLLER_QUORUM_VOTERS" to "1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093",
                "KAFKA_LISTENERS" to "CONTROLLER://0.0.0.0:9093,INTERNAL://0.0.0.0:19092,EXTERNAL://0.0.0.0:9092",
                "KAFKA_ADVERTISED_LISTENERS" to "INTERNAL://kafka-$nodeId:19092,EXTERNAL://localhost:$hostPort",
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT",
                "KAFKA_CONTROLLER_LISTENER_NAMES" to "CONTROLLER",
                "KAFKA_INTER_BROKER_LISTENER_NAME" to "INTERNAL",
                "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" to "3",
                "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" to "3",
                "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR" to "2",
                "CLUSTER_ID" to "carry-kafka-cluster-001",
            ),
        )
        c.waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1).withStartupTimeout(Duration.ofMinutes(2)))
        return c
    }

    @BeforeAll
    fun startCluster() {
        brokers = hostPorts.mapIndexed { idx, port -> broker(idx + 1, port) }
        Startables.deepStart(brokers).join() // 쿼럼 데드락 방지: 병렬 기동
    }

    @AfterAll
    fun stopCluster() {
        brokers.forEach { it.stop() }
        network.close()
    }

    private val bootstrap get() = hostPorts.joinToString(",") { "localhost:$it" }

    private fun createHaTopic(name: String) {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap)).use { admin ->
            val topic = NewTopic(name, 3, 3.toShort())
                .configs(mapOf("min.insync.replicas" to "2"))
            admin.createTopics(listOf(topic)).all().get()
        }
    }

    private fun producer() = KafkaProducer<String, String>(
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000)
        },
    )

    private fun consumeAll(topic: String, expected: Int): List<Pair<String, String>> {
        val consumer = KafkaConsumer<String, String>(
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
                put(ConsumerConfig.GROUP_ID_CONFIG, "no-loss-it")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            },
        )
        consumer.subscribe(listOf(topic))
        val out = mutableListOf<Pair<String, String>>()
        val deadline = System.currentTimeMillis() + 30000
        while (out.size < expected && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach { out.add(it.key() to it.value()) }
        }
        consumer.close()
        return out
    }

    @Test
    fun `브로커 1대 중단 후에도 발행 메시지가 무손실 소비되고 키별 순서가 보존된다`() {
        val topic = "ha.noloss.test"
        createHaTopic(topic)

        val key = "agg-1"
        val total = 200
        producer().use { p ->
            // 1차 발행
            for (i in 0 until total / 2) {
                p.send(ProducerRecord(topic, key, "msg-$i")).get()
            }
            // 브로커 1대 중단(리더 포함 가능성 유도)
            brokers[1].stop()
            // 2차 발행 — RF=3·min-ISR=2라 남은 2브로커로 무손실 지속
            for (i in total / 2 until total) {
                p.send(ProducerRecord(topic, key, "msg-$i")).get()
            }
        }

        val consumed = consumeAll(topic, total)
        // 무손실: 전건 소비
        assertThat(consumed).hasSize(total)
        // 키별 순서 보존: 같은 키라 발행 순서대로
        val values = consumed.map { it.second }
        assertThat(values).containsExactlyElementsOf((0 until total).map { "msg-$it" })
    }
}
```

- [ ] **Step 2: Docker 데몬 확인** — `docker ps > /dev/null 2>&1 && echo UP` → `UP` 아니면 Docker Desktop 기동.

- [ ] **Step 3: IT 실행**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.MultiBrokerNoLossIntegrationTest"`
Expected: PASS — 200건 전량 소비, 순서 보존.

- [ ] **Step 4: 폴백 판정** — Step 3을 연속 10회 실행. 인프라 사유(타임아웃/포트/advertised) 2회 이상 실패 시: 본 IT를 docker-compose 외부기동 가정 태그드 테스트로 전환(컨테이너 기동 코드 제거, 기동된 `localhost:9092,9094,9096` 가정). 1회 이하면 그대로 유지.

```bash
for i in $(seq 1 10); do JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test --tests "com.carry.infra.kafka.MultiBrokerNoLossIntegrationTest" --rerun-tasks -q && echo "run $i OK" || echo "run $i FAIL"; done
```

- [ ] **Step 5: 커밋**

```bash
git add carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/MultiBrokerNoLossIntegrationTest.kt
git commit -m "test(kafka): 3브로커 무손실 IT (브로커 1대 중단 후 전건 소비·순서 보존)

Testcontainers 3-node KRaft(고정포트 EXTERNAL advertise, deepStart 병렬 기동).
RF=3·min-ISR=2·acks=all 토픽에 발행 중 브로커 1대 stop → 무손실 단언.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Chunk 4: 전체 회귀 + 라이브 풀스택 스모크 + PR

### Task 7: 전체 테스트 회귀

- [ ] **Step 1: 전 모듈 컴파일**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew compileTestKotlin`
Expected: PASS.

- [ ] **Step 2: 영향 모듈 + 앱 테스트(Testcontainers saga IT 포함)**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew :carry-infra-kafka:test :carry-app:test`
Expected: PASS — 기존 EmbeddedKafka 테스트(단일 브로커 RF=1)·saga IT 무영향 확인.

- [ ] **Step 3: 전체 빌드(선택, 시간 허용 시)**

Run: `JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7" ./gradlew test`
Expected: PASS.

### Task 8: 라이브 풀스택 브로커 kill 스모크

**참고:** 이 단계는 수동 검증이며 코드 변경 없음. [[reference_carry_cdc_pipeline_broken_live]]의 교훈 — 라이브만이 직렬화/인프라 경로를 잡는다.

- [ ] **Step 1: 3브로커 풀스택 기동** (observability 스택은 리소스상 생략 가능)

```bash
cd "/c/Users/Eisen/Desktop/Labs/[projects] carry/carry-platform"
docker compose up -d postgres redis kafka-1 kafka-2 kafka-3 kafka-connect
```
> 포트 충돌 시(khala-db 5432 등) override로 postgres 포트 조정([[project_carry_remaining_backlog]] 콜드스타트 절차).

- [ ] **Step 2: 클러스터·복제 확인**

```bash
docker exec carry-kafka-1 /opt/kafka/bin/kafka-metadata-quorum.sh --bootstrap-server localhost:9092 describe --status
docker exec carry-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic ha.smoke --partitions 6 --replication-factor 3 --config min.insync.replicas=2
docker exec carry-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic ha.smoke
```
Expected: quorum 3 voters, `ha.smoke` 각 파티션 Replicas 3 / Isr 3.

- [ ] **Step 3: Debezium 커넥터 등록 + 이벤트 토픽 RF 확인**

```bash
bash infra/debezium/register-connector.sh   # 또는 register-connector.json POST
docker exec carry-kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic carry.Order.events
```
Expected: `carry.Order.events` Replicas 3 / Isr 3.

- [ ] **Step 4: 앱 부팅(otel 차단 우회 필수)**

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :carry-app:bootRun \
  "-Dspring-boot.run.jvmArguments=-Dotel.sdk.disabled=true -Dmanagement.tracing.enabled=false -Dmanagement.otlp.metrics.export.enabled=false"
```
Expected: health 200(otel 미차단 시 요청 30s+ 블록 — [[project_carry_remaining_backlog]]).

- [ ] **Step 5: 주문 생성 흐름 진행 중 브로커 1대 kill**

이벤트 발생(주문 생성/취소 등 Outbox 경로) 직후 `docker stop carry-kafka-2`. 그 상태로 추가 이벤트 발생.

- [ ] **Step 6: 무손실·복구 확인**
  - 컨슈머가 이벤트를 계속 소비(saga 진행)하는지 로그 확인.
  - `kafka-topics --describe`로 Isr가 3→2로 줄되 토픽 가용 유지.
  - `docker start carry-kafka-2` 후 Isr 2→3 재동기화 확인.
  - 발생시킨 이벤트 수 = 소비/처리 수(무손실).

- [ ] **Step 7: 스택 정리**

```bash
docker compose down   # 볼륨 보존(기본). 완전 초기화 필요 시 -v.
```

### Task 9: 이슈 + PR

- [ ] **Step 1: GitHub 이슈 등록**(한글) — 제목 예: `Kafka 멀티브로커 HA — 브로커 단일 장애점 제거 (RF=3·min-ISR=2)`. 본문에 스펙 요약 + 검증 결과.

- [ ] **Step 2: push + PR 생성**(base develop, 한글). 본문에 무손실 IT/라이브 스모크 결과·스코프 밖(클라우드/k8s) 명시. `gh pr create --base develop`.

- [ ] **Step 3: CI 그린 확인** — `gh run watch`.

- [ ] **Step 4: 사용자 게이트** — dev 머지는 사용자 승인. 머지 제안만 하고 대기.

---

## 완료 기준(Definition of Done)

- [ ] `carry.kafka.*` 외부화 + `KafkaTopicConfig` 주입(단위 4건 GREEN).
- [ ] docker-compose 3브로커 + connect 재배선(`compose config` OK).
- [ ] Debezium 이벤트 토픽 RF=3·min-ISR=2 + 계약 회귀 단언 GREEN.
- [ ] Testcontainers 3브로커 무손실 IT GREEN(폴백 판정 완료).
- [ ] 전체 `compileTestKotlin` + `:carry-infra-kafka:test` + `:carry-app:test` GREEN(기존 무영향).
- [ ] 라이브 풀스택 브로커 kill 스모크 무손실·ISR 복구 확인.
- [ ] PR(base develop) + CI 그린. dev 머지=사용자 게이트.
