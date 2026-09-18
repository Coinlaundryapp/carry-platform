# ADR-0005: 이벤트 스키마 진화 전략

## 상태

Accepted

## 날짜

2026-06-10

## 맥락

carry-platform은 Outbox + CDC(→ [ADR-0002](0002-outbox-cdc-over-dual-write.md))로 도메인 이벤트를
Kafka에 흘리고, Choreography Saga(→ [ADR-0004](0004-choreography-saga.md))로 모듈이 이벤트를
구독해 협업한다. 즉 이벤트 페이로드의 **모양(schema)**이 모듈 간 사실상의 계약이다.

### 문제 정의

이벤트를 발행/소비하는 모듈은 서로 다른 시점에 배포된다. 생산자가 이벤트에 필드를 추가하거나
바꾸면, 아직 구버전인 소비자가 역직렬화에 실패하거나 잘못 해석할 수 있다. 스키마를 어떻게
진화시켜야 모듈을 독립적으로 배포하면서도 호환이 깨지지 않는가.

### 제약

- 이벤트는 `outbox_events.payload`에 **JSON**으로 저장되고 Debezium EventRouter가 그대로 흘린다
  (envelope = `OutboxEventEnvelope`, 페이로드 = `carry-event`의 data class).
- 소비자는 `@KafkaListener`에서 `objectMapper.readValue(payload, <Event>::class.java)`로 역직렬화.
- 단일 조직·단일 코드베이스(모듈러 모놀리스). 폴리글랏·외부 컨슈머 없음.
- 운영 단순성이 1차 가치(학습/포트폴리오), 과도한 인프라(스키마 레지스트리 HA 등) 회피.

## 결정

**JSON 이벤트 + Tolerant Reader 기반의 가산적(additive) 후방·전방 호환 진화 전략을 채택한다.**
별도 스키마 레지스트리(Confluent SR 등)는 도입하지 않는다. 호환을 깨야 할 때는 필드를 바꾸지 않고
**새 이벤트 타입**을 도입한다.

### 핵심 결정 사항

1. **Tolerant Reader** — 소비자 ObjectMapper는 `FAIL_ON_UNKNOWN_PROPERTIES=false`(Spring Boot 기본).
   생산자가 추가한 미지의 필드는 구버전 소비자가 조용히 무시한다(전방 호환).
2. **가산적 진화만 허용** — 새 필드는 항상 **선택적**(nullable 또는 기본값)으로 추가한다. 기존 필드의
   타입·의미를 바꾸거나 제거하지 않는다. `OutboxEventEnvelope.traceId`/`createdAt`이 nullable 기본값인
   것이 후방 호환의 본보기다.
3. **호환을 깨는 변경 = 새 이벤트 타입** — 필드 제거/의미 변경/필수화가 불가피하면 기존 이벤트를
   변형하지 않고 새 `eventType`을 만든다. 엔벌로프의 `eventType`이 라우팅 키이므로 소비자의
   `when (eventType)`에 새 분기를 더하고, 마이그레이션 기간 동안 두 타입을 병행 소비한다.
4. **Deprecation 정책** — 제거 대상 이벤트/필드는 (a) 코드에 `@Deprecated` 표기, (b) 모든 소비자가
   신버전으로 이전될 때까지 생산 지속, (c) 소비자 이전 확인 후 제거. 최소 한 릴리스 이상 병행.
5. **스키마 레지스트리 미도입** — JSON + tolerant reader로 충분하며, SR은 레지스트리 가용성·호환성
   강제 규칙·직렬화 포맷(Avro) 전환 비용을 수반한다. 단일 조직·JSON 환경엔 과投자. **재검토 트리거**:
   외부/폴리글랏 컨슈머 등장, 또는 호환성 위반을 CI 외 런타임에서 강제해야 할 때.
6. **REST API 버전 관리** — 이벤트와 동일 원칙. 한 버전(`/v1`) 내에서는 가산적 변경만, 호환을 깨면
   `/v2`를 추가하고 deprecation 창을 둔다.
7. **`carry.saga.duration` 메트릭(연계 항목 A2)은 스키마를 키우지 않는다** — 사가 소요시간은
   종단 이벤트 수신 시점에 **애그리거트 자신의 `createdAt`**(이미 보유)과 현재 시각의 차로 계산한다.
   "필요 없는 스키마 확장보다 기존 상태 활용을 선호"라는 본 전략의 적용 사례.

### 구현 세부

정규 이벤트 전송 경로는 `OutboxEventEnvelope`(메타) + `payload`(이벤트 data class JSON)다.
`carry-event`의 `DomainEvent<T>` 래퍼는 **현재 어디서도 사용되지 않는 잔재**이며, 정규 경로가 아니다
(제거 후보 — [ADR-0001] 후속 기술부채로 추적).

호환 불변식은 `EventSchemaCompatibilityTest`로 잠근다 — 프로덕션 ObjectMapper를 만드는
`Jackson2ObjectMapperBuilder`(Spring Boot 자동설정과 동일 빌더, FAIL_ON_UNKNOWN 비활성·well-known
모듈 등록)로 동급 매퍼를 구성해 미지 필드 무시(전방), 엔벌로프 선택 필드 누락 허용(후방),
round-trip 동등성을 단언한다.

## 결과

### 긍정적

- 생산자·소비자 독립 배포 가능(가산적 변경은 양방향 무중단)
- 추가 인프라 0 — 기존 JSON 직렬화·Spring 기본 매퍼만으로 달성
- 호환 규칙이 테스트로 강제되어 회귀 차단
- `eventType` 분기 모델이 "새 타입으로 호환 깨기"를 자연스럽게 수용

### 부정적

- 스키마가 코드(data class)에만 존재 — 중앙 스키마 카탈로그·런타임 호환 강제는 없음(CI 테스트 의존)
- tolerant reader는 오타 필드(생산자 실수)도 조용히 무시 → 계약 테스트로 보완 필요
- "필드 제거 불가, 새 타입만" 규칙은 누적 시 `when` 분기·죽은 타입을 남길 수 있음(주기적 정리 필요)

### 위험

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| 비호환 변경(필드 제거·타입 변경)이 리뷰를 통과 | 중 | 고 | `EventSchemaCompatibilityTest` + 크로스모듈 계약 테스트([ADR-0002] 후속 #93)로 가드 |
| 매퍼 설정이 바뀌어 tolerant reader 깨짐 | 저 | 고 | `EventSchemaCompatibilityTest`가 빌더 기본값(FAIL_ON_UNKNOWN 비활성)을 잠금 |

## 고려한 대안

### Confluent Schema Registry + Avro

중앙 레지스트리가 스키마를 버전관리하고 호환성(BACKWARD/FORWARD)을 발행 시점에 강제.

**장점:** 런타임 호환 강제, 컴팩트한 바이너리(Avro), 외부 컨슈머에 명시적 계약.

**단점:** 레지스트리 가용성이 새 SPOF, Avro 전환·스키마 관리 운영비, JSON 디버깅 용이성 상실.

**기각 이유:** 단일 조직·JSON·모듈러 모놀리스에 과도. tolerant reader + 계약 테스트로 동일한
안전성을 인프라 없이 얻는다. 외부/폴리글랏 컨슈머가 생기면 재검토.

### 이벤트에 명시적 `schemaVersion` 필드 부여

각 이벤트에 버전 정수를 싣고 소비자가 분기.

**장점:** 버전이 데이터에 명시됨.

**단점:** 가산적 진화에는 불필요한 분기 복잡도. tolerant reader면 대부분의 변경에 버전 증가가 불필요.

**기각 이유:** 호환을 깨는 변경은 어차피 **새 `eventType`**으로 처리하므로 `eventType` 자체가
버전 식별자 역할을 한다. 별도 버전 필드는 중복.

## 참조

- `carry-event/src/main/kotlin/com/carry/event/` (이벤트 data class들)
- `carry-infra-kafka/src/main/kotlin/com/carry/infra/kafka/consumer/OutboxEventEnvelope.kt`
- `carry-app/src/test/kotlin/com/carry/app/event/EventSchemaCompatibilityTest.kt` (호환 회귀 가드)
- 관련: [ADR-0002 Outbox+CDC](0002-outbox-cdc-over-dual-write.md),
  [ADR-0004 Choreography Saga](0004-choreography-saga.md)
