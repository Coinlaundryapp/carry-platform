# Kafka 멀티브로커 HA 설계 — 브로커 단일 장애점 제거

- 날짜: 2026-06-07
- 갭: 남은 백로그 P2 #4 (분산 복원력 — IIoT/인프라 취업 서사). DLQ/retry/CircuitBreaker/파티셔닝은 완료됐으나 **브로커가 단일 장애점**으로 잔존.
- 관련 문서: [05-cdc-outbox.md](../../05-cdc-outbox.md), [2026-06-06-kafka-partitioning-design.md](2026-06-06-kafka-partitioning-design.md)

## 문제

Kafka가 단일 브로커(`docker-compose.yml`의 `kafka` 서비스 1개, KRaft 겸임 node 1)로 동작한다.
복제 계수가 전 구간 1이라 **브로커 1대만 죽어도 이벤트 토픽 전체가 소실/중단**된다:

- `KafkaTopicConfig.REPLICATION_FACTOR = 1` (DLQ 토픽).
- `register-connector.json`: `topic.creation.default.replication.factor = 1` (이벤트 토픽).
- 브로커 env: `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR = 1`, connect storage RF 1.

DLQ/retry/CircuitBreaker(PG·Geo)/파티셔닝까지 갖춘 이벤트드리븐 파이프라인의
**마지막 복원력 공백**이 브로커 HA다.

## 이미 갖춰진 전제 (실측)

- **프로듀서는 이미 HA-ready**: `application.yml`의 `spring.kafka.producer.acks=all` +
  `enable.idempotence=true`. RF≥3 + min-ISR=2가 받쳐주면 **무손실(at-least-once, 중복/재정렬 없음)**
  이 성립한다. 프로듀서 코드/설정 변경 불필요.
- **멱등 소비자** `EventConsumerSupport.processIfNotDuplicate`(eventId dedup) → 브로커 장애 후
  재전달 중복에 안전.
- **키 파티셔닝**(`aggregate_id`) → 복제·장애조치 후에도 같은 애그리거트 순서 보존.
- KRaft 단일 노드(`broker,controller` 겸임) 구조라 노드 3대 겸임으로의 확장이 자연스럽다.

## 핵심 결정

### 결정 1 — 토폴로지: 3-node 겸임 (broker + controller)

브로커 3대, 각각 `process.roles=broker,controller` 겸임(KRaft quorum voters 3).
RF=3 + `min.insync.replicas=2` + `acks=all` → **브로커 1대 다운 허용(무손실)**.

반려:
- *3 broker + 3 controller 분리(6컨테이너)*: 실운영급이나 로컬 리소스 과부하·복잡도.
- *3 broker + 1 controller*: controller가 단일 장애점 → HA 서사 훼손.

리스너 3종 분리(advertised 오설정 = 멀티브로커 최대 footgun 차단):

| 리스너 | 포트 | advertised | 용도 |
|---|---|---|---|
| CONTROLLER | 9093 | — | KRaft 쿼럼 |
| INTERNAL | 19092 | `kafka-N:19092` | 브로커 간 + kafka-connect 컨테이너 |
| EXTERNAL | 909X | `localhost:909X` | 호스트 앱/테스트 |

`KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093`,
전 브로커 동일 `CLUSTER_ID`.

### 결정 2 — RF / min.insync.replicas 프로퍼티 외부화

하드코딩 RF=3은 EmbeddedKafka 단일 브로커 테스트와 (클라우드 미준비) dev/prod를 깬다.
환경별 기변값으로 외부화한다.

신규 프로퍼티:
```yaml
carry:
  kafka:
    replication-factor: <n>
    min-insync-replicas: <n>
```

| 환경 | RF | min-ISR | 출처 |
|---|---|---|---|
| base (`application.yml`) | 1 | 1 | 안전 기본값 |
| local (`application-local.yml`) | 3 | 2 | 로컬 3브로커 |
| test (`application-test.yml`) | 1 | 1 | EmbeddedKafka 단일 — 기존 테스트 무영향 |
| dev/prod | `${CARRY_KAFKA_REPLICATION_FACTOR:1}` | `${CARRY_KAFKA_MIN_INSYNC_REPLICAS:1}` | env (클라우드 준비 후 3/2) |

`KafkaTopicConfig`:
- companion 상수 `REPLICATION_FACTOR` 제거 → `@ConfigurationProperties("carry.kafka")`
  바인딩 클래스(`replicationFactor`, `minInsyncReplicas`) 주입.
- DLQ `NewTopic`을 주입 RF로 빌드 + `TopicBuilder.config("min.insync.replicas", minInsyncReplicas.toString())`
  부여(min-ISR은 토픽 단위 설정).
- `PARTITIONS = 6`은 상수 유지(파티셔닝 설계와 정합).
- 프로듀서 설정/코드 변경 없음(이미 `acks=all`+`idempotence`).

### 결정 3 — Debezium 커넥터(로컬)

`register-connector.json`(로컬 전용, `host.docker.internal` 사용):
- `topic.creation.default.replication.factor`: 1 → 3
- `topic.creation.default.min.insync.replicas`: 2 (신규)
- `topic.creation.default.partitions`: 6 (유지)

## 검증

### 자동 통합 테스트 (carry-infra-kafka, 신규)

Testcontainers로 공유 `Network` 위에 KRaft 3브로커 클러스터를 기동:

1. RF=3, `min.insync.replicas=2` 토픽 생성.
2. `acks=all` 프로듀서로 N건 발행(키별).
3. **브로커 1대 `.stop()`** (리더 포함 케이스 유도).
4. 추가 N건 발행 + 전량 소비.
5. 단언: **무손실(전건 소비)** + **키별 순서 보존** + 중복 없음(또는 멱등 처리).

⚠️ **최대 구현 리스크 = Testcontainers 멀티브로커 advertised-listener·동적 포트 와이어링.**
호스트에서 접근하려면 EXTERNAL advertised가 매핑된 호스트 포트를 가리켜야 하는데,
Testcontainers의 매핑 포트는 컨테이너 기동 후에야 결정된다.
- 1차 접근: 컨테이너 기동 후 advertised.listeners를 매핑 포트로 갱신하는 래퍼(confluent
  `KafkaContainer`의 starter-script 패턴) 또는 고정 노출 포트.
- 폴백: 순수 Testcontainers 와이어링이 과도하게 flaky하면, **docker-compose 3브로커 스택을
  대상으로 한 태그드 테스트**(외부 기동 가정)로 무손실 단언을 수행. plan 단계에서 확정.

기존 `KafkaTopicConfigTest`(RF=1 하드 단언)는 주입값 기반으로 갱신한다.

### 라이브 풀스택 스모크 (머지 전 필수)

1. 3브로커 + postgres + redis + kafka-connect 풀스택 기동(observability 일부 생략 가능, 리소스).
2. Debezium 커넥터 등록 → 이벤트 토픽 RF=3 확인(`kafka-topics --describe`).
3. 주문 생성 → Outbox → Debezium → 컨슈머 흐름 진행 중 `docker stop kafka-2`.
4. 확인: 이벤트 소비 지속·saga 정상·**무손실**·ISR 복구(브로커 재기동 시 재동기화).

> ⚠️ 라이브 부팅 시 otel exporter가 요청을 블록 → bootRun에
> `-Dspring-boot.run.jvmArguments=-Dotel.sdk.disabled=true ...` 필요
> (`MANAGEMENT_TRACING_ENABLED=false` 단독으론 미흡, gradlew env 전파 안 됨).

## 스코프 밖

- **dev/prod yml·k8s(`k8s/base/infra/kafka.yaml`) 실 인프라 구성**: 클라우드 미준비 →
  env 기본값(`:1`)만 두고 실 멀티브로커 구성은 클라우드 준비 후 별도.
- **프로듀서 코드/설정**: 이미 `acks=all`+`idempotence`.
- **기존 EmbeddedKafka 테스트의 브로커 수**: 단일 유지(RF=1).

## 위험

| 위험 | 완화 |
|---|---|
| 리스너/advertised 오설정 (멀티브로커 1순위 footgun) | 결정1의 3-리스너 분리 + 라이브 `--describe` 검증 |
| Testcontainers 멀티브로커 포트 와이어링 flaky | 검증 섹션 폴백(docker-compose 태그드 테스트) |
| 로컬 리소스(3브로커+connect+observability 동시) | 스모크 시 observability 스택 일부 생략 |
| 기존 RF=1 단언 테스트 회귀 | `KafkaTopicConfigTest` 주입값 기반 갱신 |

## TDD 순서

1. 프로퍼티 외부화 + `KafkaTopicConfig` 주입 리팩터 (단위: RF·min-ISR 주입값이 DLQ 토픽에 반영).
2. docker-compose 3브로커 + 리스너 분리.
3. Debezium RF/min-ISR.
4. 자동 IT(무손실 + 순서 보존, 브로커 1대 stop).
5. 라이브 풀스택 스모크(브로커 kill).
6. PR (base develop, dev 머지=사용자 게이트).
