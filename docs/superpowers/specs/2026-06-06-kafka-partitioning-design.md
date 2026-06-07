# Kafka 파티셔닝 설계 — 단일 파티션 처리량 천장 제거

- 날짜: 2026-06-06
- 갭: 아키텍처 감사 CLAIM5 (WEAK) — 이벤트드리븐 처리량 천장
- 관련 문서: [05-cdc-outbox.md](../../05-cdc-outbox.md), [06-saga.md](../../06-saga.md)

## 문제

토픽 파티션 수가 기본 1로 방치되어, CDC/outbox → Kafka → saga 컨슈머 경로가
컨슈머를 아무리 늘려도 **그룹당 1 스레드**로만 동작한다(이벤트드리븐 아키텍처의
진짜 처리량 천장).

실측 결과:

- `infra/debezium/register-connector.json`: `EventRouter`가 토픽을 자동 생성하나
  파티션 수 미지정 → 브로커 기본값(1).
- `carry-app/.../application.yml`: `spring.kafka.listener.concurrency` 미설정 → 리스너당 1 스레드.
- 브로커(`k8s/base/infra/kafka.yaml`, `docker-compose.yml`): `num.partitions` 미설정 → 기본 1.

## 이미 갖춰진 전제 (실측)

- **파티션 키 = `aggregate_id`** 이미 적용됨: `register-connector.json`의
  `transforms.outbox.table.field.event.key = aggregate_id`. 따라서 파티션을 늘려도
  **같은 애그리거트 = 같은 파티션 = 순서 보장**, **다른 애그리거트 = 병렬**이 성립한다.
  saga의 이벤트 순서 의존은 키 파티셔닝으로 보존된다.
- **멱등 소비자** `EventConsumerSupport.processIfNotDuplicate`(eventId 기준 dedup,
  `@Transactional`) 이미 존재 → 동시성을 늘려도 재전달 중복에 안전.
- **DLQ + 에러 핸들러** `KafkaConfig` 완비. `dlqDestinationResolver`가 원본과
  **같은 파티션 번호**로 `.DLQ` 발행 → DLQ 토픽 파티션 수는 소스와 정합해야 한다.

## 결정

소유권을 명확히 가른다(CDC/outbox 아키텍처의 교과서 형태):

### ① 이벤트 토픽 = Debezium(producer)이 소유

`register-connector.json`에 `topic.creation.*` 추가:

- `topic.creation.default.partitions: 6`
- `topic.creation.default.replication.factor: 1`

→ `carry.*.events`가 6 파티션으로 선언 생성. `register-connector.sh`가 dev에 POST하고
k8s도 동일 JSON을 등록하므로 **전 환경 단일 소스**.

> Kafka Connect의 `topic.creation.enable`은 Kafka 2.6+ 기본 true.
> producer `enable.idempotence`는 Debezium 2.7이 쓰는 Kafka 3.x 클라이언트에서 기본 true.

### ② DLQ 토픽 = 앱이 소유

앱(`DeadLetterPublishingRecoverer`)이 발행하는 producer이므로 앱이 토픽을 소유한다.
`carry-infra-kafka`에 `KafkaAdmin.NewTopics` 빈을 신설해
`carry.{Order,Payment,Dispatch,Delivery}.events.DLQ`를 **6 파티션·RF 1**로 명시 선언.

브로커 기본 `num.partitions(1)`에 의존하지 않고 소스와 동일 파티션 수로 선언해,
`dlqDestinationResolver`의 같은-파티션 라우팅(6↔6)을 안전하게 보장한다.

### ③ 컨슈머 병렬화 + 명시적 producer 멱등

`application.yml`:

- `spring.kafka.listener.concurrency: 3` — 모듈별 리스너 최대 3 스레드, 파티션 수(6)가 상한.
- `spring.kafka.producer.acks: all` + `producer.properties.enable.idempotence: true` —
  DLQ producer. 3.x 기본 on이나 의도를 명시(문서화).

## 파라미터 근거

- **파티션 6**: 짝수 분배, 단일 브로커에서 그룹당 6-way 병렬 헤드룸. HPA 기준
  (min 3 pod × concurrency 3 = 9 컨슈머 스레드)을 6 파티션이 상한하므로 6 활성 +
  나머지 standby(페일오버). 튜너블 — 영속 가치는 "키 파티셔닝 · producer 소유 토픽" 전략.
- **RF 1**: 단일 브로커 강제. 멀티브로커 HA는 별도 스케일아웃 갭(스코프 제외).

## 의도적으로 건드리지 않는 것

- 브로커 `num.partitions` 기본(1 유지) — 이벤트·DLQ 토픽이 명시 선언되므로 의존 제거,
  blunt 전역 변경 회피.
- 파티션 키(이미 `aggregate_id`), saga 순서 로직(키 파티셔닝이 보존), 멀티브로커/복제.

## 검증

- **유닛**: `KafkaTopicConfig`의 `NewTopics` 빈이 4 DLQ 토픽을 6 파티션·RF 1로 선언하는지 단언.
- **통합**(`@EmbeddedKafka(partitions = 6)`, 기존 `KafkaErrorHandlerIntegrationTest`와 동일 하니스):
  - 같은 키 다수 발행 → 단일 파티션 + 발행 순서 보존 단언.
  - 서로 다른 키 발행 → 파티션 분산 + `concurrency: 3` 다중 스레드 소비 단언.
- **전체**: `compileTestKotlin`(cross-module) → 영향 `:test` → 머지 후 develop CI(컨테이너/통합 그린).
