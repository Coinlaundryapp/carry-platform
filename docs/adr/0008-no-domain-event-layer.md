# ADR-0008: 도메인 이벤트 계층을 두지 않는다

## 상태

Accepted (사후 기록)

## 날짜

2026-09-08

> 이 ADR 은 이미 코드에 자리 잡은 결정을 뒤늦게 문서화한 것이다. 결정 자체는 초기 모듈 구조를 잡을 때 이루어졌고, 그 뒤로 새 모듈(payment 자동과금, review 등)을 추가할 때도 같은 형태를 유지했다. 작성 시점의 코드 상태를 기준으로 사실을 확인해 기록한다.

## 맥락

carry-platform 은 헥사고날 구조의 모듈러 모놀리스이고, 모듈 간 통신은 Outbox + CDC 로 발행되는 Kafka 이벤트로만 한다([ADR-0002], [ADR-0004]). 이 구조에서 "이벤트" 를 두는 방식은 흔히 두 층으로 나뉜다.

- **도메인 이벤트**: 애그리거트가 자기 상태 변경을 인-프로세스 이벤트로 기록하고, 같은 JVM 안의 리스너가 트랜잭션 경계에 맞춰 반응한다. Spring Data 의 `@DomainEvents`/`AbstractAggregateRoot`, `ApplicationEventPublisher`, `@TransactionalEventListener` 가 대표적인 도구다.
- **통합 이벤트**: 모듈 경계를 넘어 다른 모듈(또는 다른 프로세스)이 소비하는 이벤트. 이 프로젝트에서는 `carry-event` 의 data class 와 Outbox 테이블이 담당한다.

DDD 문헌에서는 둘을 분리하고, 도메인 이벤트에서 통합 이벤트를 파생시키는 하이브리드가 자주 권장된다. 그래서 "왜 이 프로젝트에는 도메인 이벤트 계층이 없는가" 는 코드를 처음 읽는 사람이 자연스럽게 묻는 질문이고, `docs/03-architecture.md:91` 의 모듈 레이아웃 예시에 `domain/event/` 디렉터리가 그려져 있어 혼란을 키운다.

### 현재 코드 상태 (2026-09-08 확인)

- `*.kt` 전체(608 파일)에서 `@DomainEvents`, `AbstractAggregateRoot`, `ApplicationEventPublisher`, `@TransactionalEventListener`, `@EventListener` 를 사용하는 곳이 없다.
- 어떤 모듈에도 `domain/event/` 패키지가 없다.
- 애플리케이션 서비스가 `carry-event` 의 `EventPublisherPort` (`carry-event/src/main/kotlin/com/carry/event/port/EventPublisherPort.kt:8-16`) 를 통해 통합 이벤트를 발행한다. 호출 지점은 `OrderCommandService`, `DispatchCommandService`, `DispatchSagaHandler`, `DeliveryCommandService`, `PaymentCommandService`, `AutoChargeService`, `InvoiceService`, `ReviewCommandService` 의 8곳이다.
- 유일한 구현체는 `carry-infra-kafka` 의 `OutboxEventPublisher` (`carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/outbox/OutboxEventPublisher.kt:14-44`) 로, 페이로드를 JSON 으로 직렬화해 `outbox_events` 테이블에 INSERT 한다. Debezium 이 이 테이블을 WAL 에서 읽어 `EventRouter` 변환으로 `carry.<AggregateType>.events` 토픽에 실어 보낸다 (`infra/debezium/register-connector.json:12-22`).
- `carry-event` 모듈은 Jackson Kotlin 모듈 하나만 의존하고(`carry-event/build.gradle.kts`), Spring 이나 JPA import 가 없다. 내용은 모듈별 이벤트 data class (`OrderEvents.kt`, `PaymentEvents.kt`, `DispatchEvents.kt`, `DeliveryEvents.kt`, `ReviewEvents.kt`) 와 위 포트 인터페이스뿐이다. 사실상 모듈 간 Published Language 역할을 한다.
- 소비 측은 `EventConsumerSupport.processIfNotDuplicate` (`carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt:20-52`) 가 `(consumerGroup, eventId)` 복합키의 `ProcessedEvent` 를 `INSERT ... ON CONFLICT DO NOTHING` 으로 먼저 선점해 멱등성을 보장한다.
- 도메인 패키지가 Spring 에 의존하지 않는다는 규칙은 각 모듈의 ArchUnit 테스트가 강제한다 (`carry-order/src/test/kotlin/com/carry/order/architecture/HexagonalArchitectureTest.kt:39-45` 및 다른 9개 모듈의 동일 파일).

### 문제 정의

애그리거트 상태 변경을 알리는 이벤트 계층을 하나만 둘 것인가, 둘(인-프로세스 도메인 이벤트 + Outbox 통합 이벤트)로 나눌 것인가.

### 제약

- 모듈 간 통신은 이미 Outbox + CDC 로 고정돼 있다([ADR-0002]). 어떤 선택을 하든 최종 발행 경로는 Outbox 다.
- 도메인 계층은 Spring/JPA 무의존이어야 한다([ADR-0001], ArchUnit).
- 사가 핸들러와 커맨드 서비스는 멱등 재시도에서 이벤트를 재발행하면 안 된다 (`DeliveryCommandService.kt:42-43` 등의 `transitioned` 가드).

## 결정

**도메인 이벤트 계층을 두지 않는다. 이벤트는 통합 이벤트 한 층만 존재하고, 애플리케이션 서비스가 상태 전이를 수행한 뒤 `EventPublisherPort` 로 직접 발행한다.**

### 핵심 결정 사항

1. 애그리거트는 이벤트를 만들지도, 버퍼링하지도 않는다. 애그리거트 메서드의 반환값은 상태 전이가 실제로 일어났는지를 알리는 `Boolean` (또는 `PenaltyRecord` 같은 결과 값)까지다.
2. "무엇을 발행할지" 는 애플리케이션 서비스가 결정한다. 서비스는 애그리거트를 저장하고, 같은 `@Transactional` 범위 안에서 `eventPublisher.publish(aggregateType, aggregateId, eventType, payload)` 를 호출한다.
3. 발행 구현체는 `OutboxEventPublisher` 하나다. 인-프로세스 리스너, Spring 이벤트 버스, 동기 fan-out 은 없다.
4. 같은 모듈 안에서 "이벤트에 반응해야 하는 일" 이 생기면 두 가지 중 하나로 처리한다. 서비스 안에서 직접 호출하거나(예: `AutoChargeService` 가 결제 완료 후 원장 기입을 같은 트랜잭션에서 호출), 자기 모듈이 발행한 통합 이벤트를 자기 모듈이 다시 소비한다(예: `InvoiceIssuedEvent` 의 자체 소비, `docs/06-saga.md:75`).

### 왜 이렇게 판단했는가

- **발행 의미론이 하나다.** 도메인 이벤트와 통합 이벤트를 나누면 "인-프로세스 리스너에서 Outbox 로 옮겨 적는" 릴레이가 필요하고, 그 릴레이가 AFTER_COMMIT 에서 도는지 BEFORE_COMMIT 에서 도는지, 실패하면 누가 재시도하는지를 따로 설계해야 한다. Outbox INSERT 를 서비스 트랜잭션 안에서 직접 하면 이 질문이 생기지 않는다. 원자성은 이미 Outbox 가 준다([ADR-0002]).
- **애그리거트에 이벤트 버퍼 상태가 안 생긴다.** `AbstractAggregateRoot` 방식은 애그리거트가 `List<Object>` 를 들고 있다가 저장 시점에 비운다. 도메인 모델과 JPA 엔티티를 분리한 구조([ADR-0001])에서는 이 버퍼를 매퍼가 옮겨 줘야 하고, `reconstitute` 경로와 `create` 경로에서 버퍼 처리가 달라진다. 지금 애그리거트는 `_status` 같은 실제 상태만 가진다.
- **멱등 재시도와 발행 억제가 서비스 계층에 이미 있다.** 도메인 메서드가 `false` 를 돌려주면 서비스가 발행을 건너뛴다. 이벤트를 애그리거트가 수집하면 "no-op 이었을 때 이벤트를 내지 않는다" 를 애그리거트 안에서 또 한 번 표현해야 한다.
- **모듈 간 이벤트가 곧 사가 프로토콜이다.** [ADR-0004] 의 코레오그래피는 통합 이벤트만 본다. 도메인 이벤트를 따로 두면 사가에 참여하지 않는 이벤트와 참여하는 이벤트가 섞이고, 어느 쪽이 계약인지 읽는 사람이 구분해야 한다.

### 받아들인 비용

- **애그리거트가 스스로 "무슨 일이 있었는지" 를 말하지 않는다.** `Order.markDispatched` 를 호출한 뒤 `DispatchAcceptedEvent` 가 나갔는지는 애그리거트만 봐서는 알 수 없고 서비스를 읽어야 한다.
- **발행을 잊을 수 있다.** 새 상태 전이를 추가할 때 서비스에서 `publish` 를 빠뜨려도 컴파일러가 잡지 못한다. 현재는 사가 통합 테스트(`carry-app/src/test/kotlin/com/carry/app/saga/*IntegrationTest.kt`)와 서비스 단위 테스트의 `verify { eventPublisher.publish(...) }` 가 이 실수를 잡는 유일한 장치다.
- **같은 모듈 안의 부수 효과를 리스너로 분리할 수 없다.** 감사 기록, 메트릭, 원장 기입이 모두 서비스 메서드 본문에 순서대로 적힌다. 메서드가 길어지는 대신 실행 순서가 코드에 그대로 보인다.
- **자체 소비 패턴의 우회 비용.** 같은 모듈이 자기 이벤트에 반응하려면 Outbox → Debezium → Kafka → 컨슈머를 한 바퀴 돈다. `InvoiceIssuedEvent` 자동과금이 이 경로다. 인-프로세스 리스너였다면 지연이 거의 없었을 것이다.

## 결과

### 긍정적

- 이벤트 발행 경로가 한 줄이다: 서비스 → `EventPublisherPort` → `outbox_events` → Debezium → Kafka.
- 도메인 모델이 프레임워크와 이벤트 인프라 어느 쪽도 모른다. ArchUnit 규칙이 유지된다.
- 통합 이벤트 스키마([ADR-0005])가 유일한 이벤트 계약이라 진화 규칙을 한 곳에만 적용한다.

### 부정적

- 위 "받아들인 비용" 항목 그대로다. 특히 발행 누락은 테스트 커버리지에 의존한다.
- `docs/03-architecture.md:91` 의 `domain/event/` 디렉터리 그림은 이 결정과 어긋난다. 문서 수정 대상이다.

### 위험

- 모듈 내부 부수 효과가 늘어나면 서비스 메서드가 절차적으로 길어진다. 그 시점에 이 결정을 다시 볼 필요가 있다. 판단 기준은 "한 커맨드 서비스 메서드가 서로 무관한 부수 효과를 넷 이상 순서대로 호출하는가" 정도로 둔다.

## 고려한 대안

### Spring Data `@DomainEvents` + `@TransactionalEventListener(AFTER_COMMIT)` 릴레이

애그리거트(또는 JPA 엔티티)가 도메인 이벤트를 수집하고, 리포지토리 `save` 시 Spring 이 `ApplicationEventPublisher` 로 발행하며, AFTER_COMMIT 리스너가 통합 이벤트로 바꿔 Outbox 나 Kafka 에 싣는 방식이다. 저자의 다른 프로젝트(PFPlay, ADR-004)에서는 이 하이브리드를 택했다.

**장점:** 애그리거트가 자기 사건을 기술한다. 같은 모듈 안 리스너를 붙이기 쉽다.

**단점:** 이 프로젝트에서는 도메인 모델이 JPA 엔티티가 아니라서 `@DomainEvents` 가 붙을 자리가 없고, 매퍼를 거쳐 이벤트 버퍼를 옮겨야 한다. AFTER_COMMIT 리스너에서 Outbox 에 쓰면 원래 트랜잭션 밖이라 원자성이 깨지고, BEFORE_COMMIT 에 쓰면 결국 서비스에서 직접 쓰는 것과 같은 트랜잭션 안이다. 두 이벤트 층의 대응 관계(어떤 도메인 이벤트가 어떤 통합 이벤트가 되는지)를 유지하는 코드가 추가로 생긴다.

**기각 이유:** Outbox 가 이미 원자성을 주는 상황에서 릴레이 층은 의미론만 늘리고 보장은 늘리지 않는다.

### 애그리거트가 이벤트를 수집하고 리포지토리 데코레이터가 flush

애그리거트에 `pendingEvents` 리스트를 두고, `*PersistencePort` 구현체(또는 데코레이터)가 `save` 직후 리스트를 비우며 `OutboxEventPublisher` 를 호출하는 방식. Spring 이벤트 버스를 쓰지 않으므로 도메인 순수성은 지킬 수 있다.

**장점:** 발행 누락이 구조적으로 불가능해진다. 서비스가 `publish` 를 몰라도 된다.

**단점:** 애그리거트에 상태 외의 필드가 생기고, `reconstitute` 로 복원한 객체와 `create` 로 만든 객체의 버퍼 상태를 구분해야 한다. 멱등 no-op 전이에서 이벤트를 넣지 않는 조건이 애그리거트 안으로 들어온다. 이벤트 페이로드에 `carrierId` 처럼 애그리거트 밖의 값이 들어가는 경우(`AutoChargeService` 의 `orderStateQueryPort.findCarrierId`)를 애그리거트가 만들 수 없다.

**기각 이유:** 발행 누락 방지라는 이득은 있으나, 애그리거트가 자기 밖의 데이터를 알아야 페이로드를 만들 수 있는 이벤트가 이미 있어 완전한 해법이 되지 못한다. 현재는 테스트로 누락을 잡는 쪽을 택했다.

## 참조

- [ADR-0001 도메인 모델과 JPA 엔티티 분리](0001-domain-jpa-separation.md)
- [ADR-0002 Outbox + CDC](0002-outbox-cdc-over-dual-write.md)
- [ADR-0004 Choreography Saga](0004-choreography-saga.md)
- [ADR-0005 이벤트 스키마 진화 전략](0005-event-schema-evolution.md)
- [15. 불변식 카탈로그](../15-invariant-catalog.md) 6.4 절 (문서와 코드의 불일치)
- `carry-event/src/main/kotlin/com/carry/event/port/EventPublisherPort.kt`
- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/outbox/OutboxEventPublisher.kt`
- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/EventConsumerSupport.kt`
