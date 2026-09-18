<p align="center">
  <img src="docs/assets/wave-bubble.svg" width="180" alt="Carry Logo" />
</p>

<h1 align="center">Carry Platform</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/PostgreSQL-16%20+%20PostGIS-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Kafka-Outbox%20+%20Debezium%20CDC-231F20?logo=apachekafka&logoColor=white" alt="Kafka" />
  <img src="https://img.shields.io/badge/modules-24-blue" alt="24 modules" />
  <img src="https://img.shields.io/badge/tests-970-brightgreen" alt="tests" />
</p>

**운영 트래픽 없이 분산 워크플로를 끝까지 구현한 모듈러 모놀리스 — 세탁 픽업·배송 O2O 백엔드.**

고객이 세탁물을 맡기면 배달원이 수거하고, 세탁 후 다시 배달한다. 이 흐름은 여러 모듈에 걸친
**장기 실행 트랜잭션**이라, 중간에 무엇이 실패해도 상태가 갈라지지 않아야 한다. 이 저장소가 다루는
문제는 세탁이 아니라 그것이다 — **이벤트 유실 없이(Outbox + CDC), 중복 소비에 견디며(멱등 소비),
실패를 보상으로 되감는(코레오그래피 사가)** 흐름을 단일 배포 단위 안에서 강제하는 방법.

**여기부터 보면 된다.** 이 저장소가 주장하는 것과, 그 주장을 확인할 수 있는 자리다.

| | |
|---|---|
| **[ADR-0009 — 규모 가정과 파생 결정](docs/adr/0009-scale-assumptions-and-derived-decisions.md)** | 커넥션 풀 20·컨슈머 병렬도 3 같은 값이 **어떤 규모를 전제하고, 어디서 깨지는지**를 역산한다. 가정을 검증하지 않았다는 사실과, 가장 먼저 포화하는 지점을 함께 적는다. |
| **[15 — 불변식 카탈로그](docs/15-invariant-catalog.md)** | 애그리거트·사가·스위퍼가 실제로 지키는 규칙을, **무엇이 강제하는지**(도메인 `require` / DB 제약 / 관례)까지 구분해 나열한다. 코드에서 역추출했고, 확인하지 못한 항목은 `미확인`으로 남겼다. |
| **[06 — 사가 설계](docs/06-saga.md)** | 물리 흐름과 결제 흐름이 **왜 분리돼 있는지**, 보상이 어디서 걸리고 스위퍼 4종이 무엇을 되살리는지. |
| **[검증되는 것](#검증되는-것)** | 아래 표의 각 성질은 상시 실행되는 테스트가 강제한다. 절대 성능은 주장하지 않는다. |

---

## 두 개의 사가

주문의 **물리 흐름**과 **결제 흐름**은 분리돼 있다. 결제는 물리 흐름의 게이트가 아니다 —
주문 생성 시점에는 빌링키 보유와 연체 없음만 확인하고, 청구는 **수거가 끝난 뒤** 실제 무게로 발행된다.

```
물리 사가        CREATED ──► DISPATCHED ──► PICKED_UP ──► IN_PROGRESS ──► COMPLETED
                    │            │             │                             │
                    │            │             └── PickupCompleted ──┐       │
                    │            └── 배차 선점/배정/타임아웃            │       │
                    └── (취소 가능: 고객은 DISPATCHED 까지)            │       │
                                                                     ▼
결제 사가                                    Invoice: ISSUED ──► PAID ──► (REFUNDED)
                                                  │        ▲        │
                                                  │        │        └── 주문 취소 시 환불 보상
                                                  │        └── 자동과금(빌링키) · 실패 시 백오프 재시도
                                                  └── 72h 미결제 ──► OVERDUE (신규 주문 차단)
```

- **주문 상태는 물리 사실만 기술한다.** 결제 상태(청구·결제·환불)는 Invoice/Payment 소관이며
  `OrderStatus` 에는 존재하지 않는다.
- 두 흐름을 잇는 것은 **이벤트뿐**이다 — `PickupCompleted` 가 청구를 낳고, `OrderCancelled` 가 환불 보상을 낳는다.
- 멈춘 흐름을 되살리는 주체는 스위퍼 4종이다 — 배차 타임아웃 · 과금 재시도 · 연체 확정 · 정체 사가 감지.
  멀티 인스턴스에서 보상이 두 번 걸리지 않도록 ShedLock 으로 매 주기 한 노드만 실행한다.

상세: [06-saga.md](docs/06-saga.md) · 취소·보상 분기: [ADR-0004](docs/adr/0004-choreography-saga.md)

---

## 이벤트가 유실되지 않는 이유

애플리케이션은 **Kafka 에 직접 쓰지 않는다.** 상태 변경과 이벤트 발행이 같은 트랜잭션에 묶여야
dual-write 로 갈라지지 않기 때문이다.

```
서비스 트랜잭션 ─┬─ 도메인 테이블 UPDATE
                 └─ outbox_events INSERT      ← 원자적(같은 커밋)
                          │
                    Debezium(WAL) ──► carry.<Aggregate>.events ──► 컨슈머
                                                                     │
                                      (consumer_group, event_id) 선점 ─┘  ← 그룹별 멱등
```

- 컨슈머 멱등성은 `(consumer_group, event_id)` **복합키 선점**이다. `event_id` 단독이면 하나의
  이벤트를 여러 그룹이 fan-out 으로 받을 때 한 그룹만 통과하고 나머지가 굶는다(실제로 겪어서 고쳤다).
- 상태 전이는 **자연 멱등**이다 — 이미 목표 상태면 no-op 을 반환하고 이벤트를 재발행하지 않는다.
  클라이언트 멱등키가 필요한 건 생성 명령(주문·결제·리뷰)뿐이다.

상세: [05-cdc-outbox.md](docs/05-cdc-outbox.md) · [ADR-0002](docs/adr/0002-outbox-cdc-over-dual-write.md) ·
[ADR-0008](docs/adr/0008-no-domain-event-layer.md)(도메인 이벤트 계층을 두지 않는 이유)

---

## 검증되는 것

절대 처리량·지연은 **주장하지 않는다.** 아래는 머신 사양과 무관하게 성립하는 성질이고, 각 행은 상시
실행되는 테스트가 강제한다.

| 성질 | 강제하는 테스트 |
|---|---|
| 조회 쿼리 수가 **결과 건수에 비례하지 않는다**(N+1 부재) | `QueryCountGuardTest` — 10건과 20건의 SELECT 수가 같음을 단언 |
| 쓰기 경로 쿼리 수 상한 | 동일 — 주문 생성 ≤15(실측 10), 배차 선점 ≤10(실측 5) |
| 상태 변경과 이벤트 발행의 원자성 | `OutboxAtomicityIntegrationTest` |
| 그룹별 컨슈머 멱등성 | `ConsumerIdempotencyIntegrationTest` |
| 이벤트 스키마 가산적 호환 | `EventSchemaCompatibilityTest` |
| 동시 요청 하의 불변식(배차 선점 1인) | `ConcurrencyIntegrationTest` |
| 사가 보상 | Order · OrderCancellation · Dispatch · Delivery · AutoCharge 통합 테스트 |
| 정산 원장 Σ=0 · PG 대사 | `SettlementLedgerIntegrationTest` · `PaymentReconciliationIntegrationTest` |
| 원장 append-only(UPDATE/DELETE 거부) | `LedgerAppendOnlyIntegrationTest` — DB 트리거까지 확인 |
| 연체 확정 가드가 전이표와 일치 | `InvoiceOverdueGuardIntegrationTest` — 모든 상태 순회 |
| 고객당 활성 빌링키 1개 | `BillingKeyActiveUniqueIntegrationTest` — 부분 유니크 인덱스 확인 |
| **DB 경로 단절 시 유한 시간 실패·자동 회복·풀 상한 유지** | `DatasourceOutageChaosTest`(Toxiproxy) |
| 모듈 경계·헥사고날 계층 | `ModuleBoundaryTest` · 모듈별 `HexagonalArchitectureTest`(ArchUnit) |

**장애 주입이 실제로 결함을 잡았다.** Toxiproxy 로 DB 경로를 블랙홀 처리하자 `SELECT 1` 이
**13,424,276ms(3시간 43분)** 반환되지 않았다 — `connection-timeout` 은 커넥션 *획득* 에만 적용되고
소켓 읽기는 pgjdbc `socketTimeout`(기본 0=무한) 소관이었기 때문이다. `socketTimeout: 10` 적용 후
**11.2초**에 실패한다. 이 테스트가 회귀 가드로 남아 있다.

---

## 모듈 구조

24개 Gradle 서브모듈. 도메인 모듈은 **다른 도메인 모듈을 `project(...)` 로 의존하지 않는다** —
크로스모듈 동기 조회는 소비자가 정의한 쿼리 포트를 `carry-app` 의 어댑터가 구현해 잇는다.

```
carry-app                 조립 + 크로스모듈 쿼리 포트 어댑터 5종 + 통합 테스트
├─ 도메인 13              order · payment · dispatch · delivery · user · laundromat
│                         price · geo · review · notification · media · operation
│                         service-availability(내부 전용, API 미노출)
├─ 기반 4                 common(응답·예외·검증 헬퍼) · event(이벤트 계약)
│                         security(JWT) · audit(감사 로그)
├─ 인프라 5               persistence · kafka(Outbox·컨슈머) · redis · s3 · observability
└─ loadtest               Gatling 시나리오
```

각 도메인 모듈 내부는 `domain` / `application`(port.inbound·port.outbound·service) /
`adapter`(inbound.rest·inbound.kafka·outbound.persistence) 3계층이며 ArchUnit 이 강제한다.
도메인 모델은 JPA 무의존이고 JPA 엔티티는 어댑터에 따로 있다([ADR-0001](docs/adr/0001-domain-jpa-separation.md)).

| 모듈 | 대표 API |
|---|---|
| `carry-order` | `POST /api/v2/orders` · `GET /api/v2/orders/my` |
| `carry-payment` | `POST /api/v2/billing-keys` · `GET /api/v2/payments/{orderId}/invoice` |
| `carry-dispatch` | `GET /api/v2/dispatches/available` · `POST /api/v2/dispatches/{dispatchId}/claim` |
| `carry-delivery` | `POST /api/v2/deliveries/{deliveryId}/pickup` · `.../washing` · `.../drying` · `.../delivery` |
| `carry-user` | `GET /api/v2/users/me` · `POST /api/v2/shipping-addresses` |
| `carry-laundromat` | `GET /api/v2/laundromats`(PostGIS 인근 검색) |
| `carry-review` | `POST /api/v2/reviews` · `GET /api/v2/reviews/laundromat/{laundromatId}/statistics` |
| `carry-operation` | `GET /api/v2/admin/dashboard/summary` · `GET /api/v2/terms` |

코디네이터 전용 경로는 `/api/v2/coordinator/*`, 관리자 전용은 `/api/v2/admin/*` 이며 역할 가드가
걸려 있다(`RoleGuardTest` 가 올바른 역할·잘못된 역할 양방향으로 확인한다).
전체 70개 엔드포인트는 [openapi-v2.json](docs/api/openapi-v2.json) 에 고정돼 있다.

---

## 시작하기

```bash
# 1. 인프라 (postgres+PostGIS · redis · kafka 3-node · connect · localstack)
./scripts/dev-up.sh

# 2. 애플리케이션
MANAGEMENT_TRACING_ENABLED=false SPRING_PROFILES_ACTIVE=local ./gradlew :carry-app:bootRun
#    트레이싱 export 를 끄지 않으면 otel exporter 가 요청을 블록한다

# 3. 개발용 토큰 (비프로덕션 전용)
curl -X POST localhost:8080/api/v2/auth/dev-login \
     -H 'Content-Type: application/json' -d '{"role":"CARRIER"}'
```

Swagger UI `http://localhost:8080/swagger-ui.html` · 스키마 갱신 `./scripts/export-openapi.sh`

```bash
./gradlew test            # 전 모듈. Testcontainers(postgis/postgis:16-3.4)가 필요하다
./gradlew :carry-app:test # 사가·동시성·멱등성·장애주입 통합 테스트
```

스키마는 모든 환경에서 Flyway 가 관리하고 `ddl-auto: validate` 다. 마이그레이션을 고쳤거나 스키마가
의심스러우면 `./scripts/db-reset.sh` 로 볼륨을 비우고 다시 올린다(Debezium 슬롯도 함께 사라지므로
`dev-up.sh` 재실행). 테스트 구성은 [08-testing.md](docs/08-testing.md) 참조.

---

## 클라이언트

세 개의 웹앱이 이 API 를 소비한다([carry-app](https://github.com/Coinlaundryapp/carry-app) 저장소).

| 앱 | 형태 | 역할 |
|---|---|---|
| customer-web | 설치형 PWA + 네이티브 웹뷰 | 주문·결제수단 등록·상태 추적 |
| carrier-web | 설치형 PWA | 배차 선점·수거/세탁/건조/배달 단계 처리 |
| coordinator-web | 어드민형 웹앱 | 주문·배차 모니터링, 강제 배정·운영 취소 |

---

## 정직한 범위와 한계

**이것은 운영 트래픽이 없는 아키텍처 실증이다.** 아래는 짧은 형태이고, 긴 형태는
[ADR-0009 §4·§5](docs/adr/0009-scale-assumptions-and-derived-decisions.md) 와
[10-production-readiness.md](docs/10-production-readiness.md) 에 있다.

- **규모 가정은 검증되지 않았다.** 일 주문 10만·피크 배수 8 같은 값은 도메인 상식에 기댄 것이며
  실트래픽으로 확인된 바 없다. ADR-0009 는 그 가정에서 각 구성값을 유도하고, **가정이 틀리면 어떤 결정이
  함께 무너지는지**를 적는다. 목적은 값의 정확성이 아니라 값과 결정의 연결을 추적 가능하게 두는 것이다.
- **절대 처리량·지연은 측정했지만 주장하지 않는다.** Gatling 측정은 로컬 docker 풀스택에 3개 시나리오
  합계 가상유저 100명을 30초에 램프업하는 규모다. 환경 의존이 커 CI 게이트에서 제외했다.
- **가장 먼저 포화하는 곳은 Kafka 컨슈머 병렬도 3이다.** 가정한 규모에서 컨슈머당 여유가 거의 없다.
  파티션과 `concurrency` 를 함께 올려야 하고, 그때 파티션 키(`orderId`)가 순서 도메인과 일치하는지
  다시 확인해야 한다.
- **장애 주입은 DB 경로만 했다.** Kafka·PG 경로는 아직이다. DB 측 `statement_timeout` 병행도 남았다.
- **결제 PG production 어댑터가 없다.** local/e2e 는 스텁 어댑터로 결제·환불을 완주하지만, prod 빈은
  없어 `resolve()` 가 throw 한다. 실 Toss 키 배선이 남은 빚이다.
- **클라우드·K8s 는 검증되지 않았다.** prod 매니페스트·배포 워크플로는 작성돼 있으나 실제로 올려본 적이
  없다. prod DB 는 PostGIS 가능 이미지여야 한다(인근 검색이 `ST_DWithin` 에 의존).
- **운영 절차가 없다.** 정산 마감·지급 실행·규제 대응 런북은 없다. 알럿 룰과 런북 8종은 있으나
  실 Slack/PagerDuty webhook 은 미주입이다.
- **관측 대시보드는 라이브 검증이 남았다.** 21패널 단일 대시보드를 테마별로 분리하는 작업, 캐시 hit
  실증(Naver 키 필요)이 미완이다.
- 개발 과정에 AI 보조를 사용했고, 설계 판단과 테스트 결과는 작성자가 직접 검증했다.

---

## 문서

| | |
|---|---|
| [아키텍처 의사결정](docs/adr/) | ADR-0001 도메인/JPA 분리 · 0002 Outbox+CDC · 0003 모듈 분리 기준 · 0004 코레오그래피 사가 · 0005 이벤트 스키마 진화 · 0006 모듈 과분해 재평가 · 0007 모듈러 모놀리스 유지 · 0008 도메인 이벤트 계층 없음 · 0009 규모 가정 |
| [코드에서 역추출한 문서](docs/15-invariant-catalog.md) | 15 불변식 카탈로그 · [16 컨텍스트 맵](docs/16-context-map.md) · [17 유비쿼터스 언어](docs/17-ubiquitous-language.md) |
| [운영·관측](docs/12-observability-stack.md) | 12 관측 스택 · [13 로깅 정책](docs/13-logging-policy.md) · [operations/](docs/operations/) 런북 |
| [클라이언트 가이드](docs/14-client-retry-guide.md) | 에러 코드별 재시도 가능 여부 · `Idempotency-Key` · 백오프 |
| [기여 가이드](CONTRIBUTING.md) | 아키텍처 규칙 · 3-Method 패턴 · 테스트 기준 · 커밋/PR 규약 |

전체 문서 목록은 [docs/01-overview.md](docs/01-overview.md) 하단 표에 있다.

### 마이크로서비스 전환 — 보류 ([ADR-0007](docs/adr/0007-modular-monolith-over-microservices.md))

분산 패턴(Outbox+CDC · 코레오그래피 사가 · 멱등 소비 · 강제된 모듈 경계)은 단일 배포 단위에서 이미
시연된다. 1인·비프로덕션 맥락에서 실제 분리는 이득 대비 비용이 크다. 전환 트리거(트래픽 격차 · 팀 분리 ·
규제 격리 · 클라우드 준비)에 도달하면 `carry-payment`·`carry-notification` 이 가장 먼저 분리 가능하고,
바뀌는 것은 `carry-app` 의 쿼리 포트 어댑터 구현뿐이다.

---

## 라이선스

이 저장소는 포트폴리오·학습 목적으로 공개돼 있다.
