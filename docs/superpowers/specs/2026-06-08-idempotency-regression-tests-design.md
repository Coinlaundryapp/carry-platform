# 멱등성 회귀 테스트 설계 (ROADMAP 5.3)

> 작성일: 2026-06-08 · 기준: develop `65cc5dc`(Clock 주입 #86 반영 후)
> 백로그: `2026-06-07-carry-remaining-backlog.md` P2 #5 "명시적 멱등성 테스트"
> 스코프 결정(사용자): **테스트전략 only** — 기존 소비자측 dedup 회귀 테스트만. 명령측 idempotency key는 인프라 부재로 범위 밖(별도 기능 항목).

## 1. 배경 / 문제

코레오그래피 saga의 이벤트 소비는 단일 진입점 `EventConsumerSupport.processIfNotDuplicate(eventId, …, block)`를 통해 중복 제거된다:

```kotlin
@Transactional
fun processIfNotDuplicate(eventId, traceId, eventType, block) {
    if (processedEventRepository.existsById(eventId)) { return }   // 이미 처리 → skip
    … block() …                                                    // 부수효과
    processedEventRepository.save(ProcessedEvent(id = eventId))     // 마킹
}
```

6개 모듈 컨슈머(Order/Payment/Dispatch/Delivery/Notification/Operation)가 모두 이 헬퍼를 사용하며, dedup 키로 `envelope.id`(Outbox 행 UUID)를 넘긴다.

**갭**: 이 멱등성 보증 경로는 현재 **테스트 커버리지 0**. 기존 saga 통합테스트들은 `sagaHandler.onXxx()`를 직접 호출해 컨슈머/`EventConsumerSupport`를 우회한다. at-least-once 전달(Kafka·CDC 재처리·리밸런스)에서의 중복 안전성이 회귀로 깨져도 잡히지 않는다.

## 2. 보호할 불변식

> **동일 도메인 이벤트가 2회 이상 배달돼도 그 부수효과는 최대 1회 커밋된다.**

메커니즘 2계층:
- **L1 — dedup 로직**: `processIfNotDuplicate`의 existsById/save + 트랜잭션 경계. 진짜 동시성 안전성의 근거는 `processed_events.id` **PK unique 제약**이다(존재체크는 race window가 있고, 최종 보증은 제약 위반→tx 롤백→부수효과 롤백).
- **L2 — 키 배선**: 각 컨슈머가 per-delivery 값(record offset/key)이 아니라 **안정적 고유 `envelope.id`**를 dedup 키로 넘긴다.

## 3. 테스트 매트릭스 (4 레이어 / 3 파일)

| # | 레이어 | 위치 | 가드 대상 |
|---|--------|------|-----------|
| ① | 단위 — `EventConsumerSupport` 제어흐름 | `carry-infra-kafka` (mockk) | L1 분기 로직 |
| ② | 통합(순차) — 실DB dedup | `carry-app` (Testcontainers) | L1 실영속 |
| ③ | 통합(동시) — PK-race net-once | `carry-app` (Testcontainers) | L1 동시성 보증 |
| ④ | 단위 — 대표 컨슈머 키 배선 | `carry-order` (mockk) | L2 |

### ① 단위 — `EventConsumerSupportTest` (carry-infra-kafka)
mockk로 `ProcessedEventRepository`·`Tracer`(relaxed) 주입. DB 없음. 케이스:
- **신규 id**: existsById=false → `block` 1회 실행 + `save(ProcessedEvent(id))` 1회.
- **기존 id**: existsById=true → `block` **미실행** + `save` 호출 없음.
- **block 예외**: 예외가 호출자에게 전파되고 `save` **호출 안 됨**(실패 이벤트는 미마킹 → at-least-once 재처리 가능). ⚠️ 이 속성이 깨지면 실패 이벤트가 "처리됨"으로 표시돼 영구 유실.

### ②③ 통합 — `ConsumerIdempotencyIntegrationTest` (carry-app)
`IntegrationTestBase`(postgres Testcontainers) 확장. `EventConsumerSupport`·`ProcessedEventRepository`·`JdbcTemplate` autowire.

관측 가능한 부수효과: `@BeforeEach`에서 raw JDBC로 marker 테이블 생성(JPA 엔티티 아님 → `ddl-auto: validate`와 무충돌). `block`은 이 테이블에 행을 insert. marker insert는 `processIfNotDuplicate`의 트랜잭션에 참여하므로 tx 롤백 시 함께 롤백된다.

```sql
CREATE TABLE IF NOT EXISTS idem_test_marker (seq bigserial primary key, event_id varchar(255));
```

- **②순차**: 같은 eventId로 `processIfNotDuplicate` 2회 호출 → marker 행 1개, `processed_events`의 그 id 1행. 두 번째 호출의 block 미실행을 실DB로 증명.
- **③동시**: CountDownLatch + 고정 스레드풀로 K=8 스레드가 동일 eventId 동시 호출 → **`processed_events` 그 id 정확히 1행**(DB PK dedup 안전망) + marker ∈ [1, K]. 이것이 동시성에서 결정적으로 참인 불변식이다.

> ⚠️ **동시성에서 "block 정확히 1회"는 단언하지 않는다(현 구현이 보장 안 함).** `ProcessedEventRepository.save()`는 ProcessedEvent가 할당식 @Id·non-Persistable이라 `merge()`(SELECT 선행)로 동작 → 패자 스레드의 merge-SELECT가 승자 커밋 이후 실행되면 INSERT가 아닌 no-op UPDATE로 PK 위반 없이 커밋(이미 실행한 block의 부수효과 잔존, marker>1 가능). 이는 **현실 위협모델에서 무해**: Kafka가 같은 키 이벤트를 같은 파티션→단일 스레드로 순차 처리하므로 동일 이벤트의 진짜 동시 소비가 없고 재배달도 순차(순차 dedup·실패 롤백은 ②②b가 보장). 동시성에서 신뢰하는 안전망은 "처리 마킹 1행"이며 그것만 단언한다. 타이밍 의존 단언(race-경로별 marker 정확값·패자 예외 수)은 금지(Kafka 파티셔닝 flaky 교훈). **⚠️ 이 정정은 CI(느린 러너)에서 marker==1 단언이 flaky 실패하며 발견됨 — 로컬 단독 실행은 타이밍이 INSERT 경합으로 수렴해 미노출.**
>
> 📌 **후속 옵션(별도 결정)**: block-at-most-once를 진짜 동시성에서도 보장하려면 claim-first 패턴(ProcessedEvent를 Persistable로 → `saveAndFlush`를 block 이전에 호출, PK 위반 시 skip)으로 프로덕션 하드닝 가능. 현 위협모델상 불요라 보류, 사용자 결정 대기.

`@AfterEach`: marker drop + `DELETE FROM processed_events`로 격리.

### ④ 단위 — `OrderEventConsumerIdempotencyTest` (carry-order)
대표 컨슈머 `OrderEventConsumer`를 mock `OrderSagaEventHandler` + fake/mock `EventConsumerSupport` + 실 `ObjectMapper`로 직접 와이어링. 동일 `OutboxEventEnvelope` JSON(같은 `id`, eventType=`DispatchAcceptedEvent`)을 `consumeDispatchEvents`에 2회 공급 → `sagaHandler.onDispatchAccepted` **1회** 호출. 컨슈머가 `envelope.id`를 dedup 키로 넘김을 증명(L2). dedup은 in-memory set 기반 fake repo로 결정적 재현.

## 4. 범위 밖 (명시)
- **명령측 idempotency key**: `createOrder` 등에 Idempotency-Key 헤더/요청 dedup 테이블 부재. 신규 기능이며 API 설계 포함 → 별도 백로그 항목.
- **나머지 5개 컨슈머 개별 키-배선 테스트**: 컨슈머들은 동일 보일러플레이트(`objectMapper.readValue` → `processIfNotDuplicate(envelope.id, …)`)라 대표 1개(④)로 충분. 6개 전수 복제는 YAGNI.
- **Kafka 브로커 통한 실 end-to-end 중복 배달**: EmbeddedKafka로 띄울 수 있으나 ①~④가 L1·L2를 결정적으로 덮어 추가 가치 대비 비용·flaky 위험 큼.

## 5. 검증 흐름
전체 `compileTestKotlin` → `:carry-infra-kafka:test` `:carry-order:test` → `:carry-app:test`(Testcontainers ②③) → PR(base develop). 프로덕션 코드 변경 0(테스트 전용)이라 부팅 경로 불변 → 라이브 풀스택 스모크는 불필요(부팅·런타임 동작 무변경). dev 머지 = 사용자 게이트.
