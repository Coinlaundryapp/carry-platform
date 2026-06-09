# ADR-0002: Outbox + CDC(Debezium) 채택 — dual-write 회피

## 상태

Accepted

## 날짜

2026-06-09

## 맥락

carry-platform은 Choreography Saga(→ [ADR-0004](0004-choreography-saga.md))로 모듈 간 워크플로우를
조정하며, 도메인 상태 변경 시 도메인 이벤트를 Kafka로 발행해야 한다.

### 문제 정의

"DB에 비즈니스 상태를 쓰는 것"과 "Kafka로 이벤트를 발행하는 것"은 서로 다른 두 시스템에 대한 쓰기다.
이를 순진하게 둘 다 직접 수행하면 **dual-write 문제**가 발생한다.

- DB 커밋은 성공했는데 Kafka 발행이 실패 → 다른 모듈이 영영 모르는 상태 변경(이벤트 유실)
- Kafka 발행은 성공했는데 DB 트랜잭션이 롤백 → 일어나지 않은 일에 대한 이벤트(유령 이벤트)

두 시스템에 걸친 원자성은 분산 트랜잭션(2PC) 없이는 보장되지 않으며, Kafka는 XA 트랜잭션에
적합하지 않다.

### 제약

- 이벤트 발행은 비즈니스 상태 변경과 **원자적**이어야 한다(전부 또는 전무)
- 애그리거트별 이벤트 **순서**가 보장되어야 한다(Saga 상태 전이가 순서에 의존)
- PostgreSQL(논리 복제 가능)·Kafka(3-node KRaft, RF=3/min-ISR=2)가 이미 인프라에 존재
- 애플리케이션 코드는 "이벤트가 결국 발행된다"는 사실만 신뢰하면 되도록 단순하게

## 결정

**Transactional Outbox 패턴 + 로그 기반 CDC(Debezium)를 채택한다.**
도메인 상태 변경과 같은 DB 트랜잭션에서 `outbox_events` 테이블에 이벤트를 INSERT하고,
Debezium이 PostgreSQL WAL을 tailing하여 Kafka로 발행한다. 애플리케이션은 Kafka에 직접 produce하지 않는다.

### 핵심 결정 사항

1. **단일 `outbox_events` 테이블** (`carry-infra-kafka`의 `OutboxEvent`) — 컬럼:
   `id`(UUID), `aggregate_type`, `aggregate_id`, `event_type`, `payload`(JSONB), `trace_id`, `created_at`.
2. **발행 = 같은 트랜잭션 내 INSERT** — 도메인 서비스의 `@Transactional` 블록에서
   애그리거트 저장과 `EventPublisherPort.publish()`(= `outbox_events` INSERT)가 함께 커밋된다.
   `OutboxEventPublisher`는 `repository.save()`만 수행하며 **폴링 릴레이가 없다**(로그 기반 CDC에 위임).
3. **Debezium EventRouter SMT로 라우팅** — `infra/debezium/register-connector.json`이
   `aggregate_type` → 토픽 `carry.<AggregateType>.events`로 라우팅하고, `aggregate_id`를
   Kafka 메시지 키로 설정(= 같은 애그리거트 = 같은 파티션 = 순서 보장).
4. **소비측 멱등 처리** — CDC/Kafka는 at-least-once이므로 `EventConsumerSupport.processIfNotDuplicate(eventId)`가
   `processed_events` 테이블로 중복을 제거한다. 멱등 키는 Kafka offset이 아니라 `envelope.id`(이벤트 고유 ID).

### 구현 세부

발행 흐름 전체:

```
도메인 서비스 @Transactional
  ├─ 애그리거트 저장 (JPA)
  └─ EventPublisherPort.publish() → outbox_events INSERT
        ↓ (단일 DB 커밋)
PostgreSQL WAL (wal_level=logical)
        ↓
Debezium PostgresConnector (table.include.list = public.outbox_events)
  └─ EventRouter SMT: aggregate_type → carry.<X>.events, aggregate_id → key
        ↓
Kafka (RF=3, min.insync.replicas=2)
        ↓
@KafkaListener → OutboxEventEnvelope 역직렬화
  └─ processIfNotDuplicate(envelope.id) → SagaHandler
```

커넥터 설정의 핵심은 `transforms.outbox.table.expand.json.payload=false`(payload를 문자열로
유지해 컨슈머가 재파싱)와 `...fields.additional.placement`(envelope 메타필드 합성)이며,
이는 `OutboxConnectorContractTest`로 회귀 방어된다.

## 결과

### 긍정적

- **원자성**: 상태 변경과 이벤트 기록이 단일 DB 트랜잭션 → dual-write 불일치 원천 차단
- **신뢰성 위임**: 발행 신뢰성이 Debezium 인프라로 빠지고 애플리케이션 코드는 단순(DB 커밋만 신경)
- **순서 보장**: `aggregate_id` 키 파티셔닝으로 애그리거트별 이벤트 순서 유지
- **추적성**: `trace_id`를 outbox에 실어 분산 추적이 이벤트 경계를 넘어 이어짐
- **HA 무손실**: RF=3/min-ISR=2 + 멱등 소비로 브로커 1대 다운에도 유실·중복 안전

### 부정적

- **운영 복잡도**: Debezium Connect·커넥터 등록·논리 복제 슬롯 등 추가 운영 요소
- **종단 지연**: 동기 발행 대비 WAL→CDC→Kafka 경유로 약간의 지연(eventual)
- **outbox 적체**: CDC 정체 시 `outbox_events` 테이블 증가(정리/모니터링 필요)
- 컨슈머가 항상 멱등을 전제로 작성되어야 함(at-least-once)

### 위험

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| 커넥터 envelope 매핑 오류(컨슈머 역직렬화 실패) | 중 | 고 | `OutboxConnectorContractTest`로 placement·payload 설정 회귀 가드 |
| 부동 태그 이미지로 인한 비재현성 | 중 | 중 | `debezium/connect:2.7.3.Final` 패치 버전 핀 (라이브 스모크에서 발견·수정) |
| Connect 내부 토픽 RF=1 잔존(SPOF) | 중 | 고 | `CONNECT_*_STORAGE_REPLICATION_FACTOR=3` 명시 (라이브 스모크에서 발견·수정) |
| 멱등 마커 동시성 경합 | 저 | 중 | 같은 키→같은 파티션→단일 스레드 순차 소비로 현실 위협모델상 안전(회귀 테스트 #90/#91) |

## 고려한 대안

### Dual-write (DB 커밋 후 직접 Kafka produce)

**장점:** 구현 단순, 추가 인프라 없음, 지연 최소.

**단점:** 두 시스템에 걸친 원자성 부재 → 이벤트 유실/유령 이벤트. 부분 실패 처리 코드가
모든 발행 지점에 흩어짐.

**기각 이유:** 본 프로젝트가 시연하려는 핵심이 "신뢰성 있는 분산 이벤트 전파"다.
dual-write는 그 목표와 정면 충돌한다.

### 폴링 기반 Outbox 릴레이 (@Scheduled로 테이블 폴링 후 발행)

Outbox 테이블에 쓰되, CDC 대신 애플리케이션 스케줄러가 미발행 행을 주기적으로 읽어 Kafka로 produce.

**장점:** Debezium 불필요. 발행 로직이 애플리케이션 내부라 디버깅 직관적.

**단점:** 폴링 주기-지연 트레이드오프, 폴링 부하, 발행 상태(`published` 플래그) 관리,
스케줄러가 애플리케이션 인스턴스에 결합(리더 선출 등 필요).

**기각 이유:** 로그 기반 CDC가 폴링 부하·지연 없이 WAL을 그대로 흘려보내며, 발행 책임을
애플리케이션에서 완전히 분리한다. 분산 시스템 패턴 시연 관점에서 CDC가 더 모범적이다.

### 분산 트랜잭션(2PC / XA)

DB와 Kafka를 XA로 묶어 진짜 원자 커밋.

**장점:** 이론적 강한 일관성.

**단점:** Kafka의 XA 지원이 빈약·비권장, 코디네이터 장애·블로킹·성능 저하.

**기각 이유:** 운영 복잡도와 가용성 손실이 크고, Outbox가 동일한 효과(유실 없는 발행)를
훨씬 가볍게 제공한다.

## 참조

- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/outbox/OutboxEvent.kt`
- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/outbox/OutboxEventPublisher.kt`
- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt`
- `infra/debezium/register-connector.json`
- `carry-infra-kafka/src/test/kotlin/com/carry/infra/kafka/OutboxConnectorContractTest.kt`
- 관련: [ADR-0004 Choreography Saga](0004-choreography-saga.md)
