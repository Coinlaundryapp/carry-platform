# 크로스모듈 Consumer-Driven Contract 테스트 설계 (ROADMAP 5.2)

> 작성일: 2026-06-08 · 기준: develop `14e81ee`(멱등성 #90/#91 반영 후)
> 백로그: `2026-06-07-carry-remaining-backlog.md` P2 #6 "Contract Test"
> 스코프 결정(사용자): **정식 CDC 패턴**(포트폴리오) · **하이브리드 충실도**(경량 4 + UserQueryPort 스모크 IT 1) · **소비자 모듈 testFixtures 소유** · **옵션 B**(기존 소비자 단위테스트 mock을 검증된 Fake로 교체).

## 1. 배경 / 문제

Carry는 22모듈 모듈러 모놀리스다. 한 모듈이 **다른 모듈이 소유한 데이터**를 필요로 할 때, 소비자 모듈이 outbound `*QueryPort` 인터페이스를 정의하고, 조립 모듈 `carry-app`이 그 포트를 provider의 inbound use-case에 위임하는 **어댑터**로 구현한다.

크로스모듈 쿼리 봉합점(seam)은 4곳이다:

| 소비자 포트 | provider use-case | 어댑터(carry-app) | 계약의 핵심 |
|---|---|---|---|
| `order/UserQueryPort` | `user/ShippingAddressUseCase` | `UserQueryPortAdapter` | `ShippingAddress`→`OrderShippingAddress` **9필드 매핑** (nullable `entranceInfo`) |
| `delivery/PaymentQueryPort` | `payment/PaymentQueryUseCase` | `PaymentQueryPortAdapter` | `isOrderPaid` boolean 패스스루 |
| `order/LaundromatQueryPort` | `laundromat/LaundromatQueryUseCase` | `LaundromatQueryPortAdapter` | **예외-as-시그널**: `getById`가 `LaundromatNotFoundException` throw → `existsById=false` |
| `order/ServiceAvailabilityQueryPort` | `service-availability/...QueryUseCase` | `ServiceAvailabilityQueryPortAdapter` | **예외-as-시그널**: area 부재/시간 불가 시 throw passthrough |

> ⚠️ 백로그가 적은 `OrderQueryPort`는 실재하지 않음. 실제 포트는 위 4개(User·Payment·Laundromat·ServiceAvailability).

**갭 1 — 어댑터 테스트 0**: 4개 어댑터 모두 테스트 커버리지가 없다. provider 모듈이 시그니처/반환shape/예외계약을 바꾸면 어댑터의 변환이 조용히 깨진다(특히 예외-as-시그널 2곳, 9필드 매핑 1곳).

**갭 2 — 소비자 mock 드리프트**: 소비자(`OrderCommandServiceTest`·`DeliveryCommandServiceTest`)는 4개 포트를 **인라인 mockk stub**으로 흉내 낸다. mockk는 "시킨 대로" 반환할 뿐 real provider 동작과의 일치를 검증하지 않으므로, provider가 바뀌어도 소비자 테스트는 옛 가정으로 계속 통과한다(거짓 GREEN).

## 2. 보호할 불변식

> **소비자가 포트에 대해 기대하는 행위(반환 shape·매핑·예외-as-시그널)를 real 어댑터+provider 서비스가 실제로 충족하며, 소비자가 단위테스트에서 쓰는 test-double도 같은 기대를 충족한다.**

이를 위해 각 포트마다 **추상 계약(consumer-owned)**을 한 곳에 못박고, **두 구현이 같은 계약 절을 통과**시킨다:
1. **소비자측** — 손으로 쓴 공유 `Fake<Port>` (소비자가 단위테스트에서 사용).
2. **provider측** — real `<Port>Adapter`(real provider 서비스 + in-memory fake persistence로 와이어).

이로써 소비자의 mental model(계약 절)에서 ①real 어댑터와 ②소비자 test-double이 **구조적으로 드리프트할 수 없다**(CDC keystone).

## 3. 아키텍처 — "하나의 계약, 두 구현이 통과"

```
┌─ carry-order/src/testFixtures (소비자 소유) ──────────────┐
│  abstract UserQueryPortContract                            │  ← 계약 절(@Test) = 소비자 기대
│    protected abstract fun subject(): UserQueryPort         │
│    protected abstract fun arrangeAddress(u,a,expected)     │  ← 상태 준비 훅(양측 각자 방식)
│    protected abstract fun arrangeMissingAddress(u,a)       │
│  class FakeUserQueryPort : UserQueryPort                   │  ← 검증된 공유 test-double
└────────────────────────────────────────────────────────────┘
      ▲ 통과측 1 (소비자측)                ▲ 통과측 2 (provider측)
┌─ carry-order/src/test ───────────┐  ┌─ carry-app/src/test ───────────────────────────┐
│ FakeUserQueryPortContractTest    │  │ UserQueryPortAdapterContractTest                │
│   : UserQueryPortContract        │  │   : UserQueryPortContract                       │
│   subject()=FakeUserQueryPort    │  │   subject()=UserQueryPortAdapter(               │
│   arrange→fake에 put             │  │     ShippingAddressService(FakeShippingAddrPersist))│
│   ⇒ "fake가 계약에 충실"          │  │   arrange→fake persistence에 insert             │
└──────────────────────────────────┘  │   ⇒ "real 어댑터+서비스가 계약 준수"            │
                                       └──────────────────────────────────────────────────┘
```

**핵심 메커니즘 — 추상 arrange 훅**: 계약 절은 "상태를 준비하고 → subject 호출 → 결과 단언"이다. 상태 준비 방식이 양측에서 다르므로(fake에 직접 put vs fake persistence에 provider-domain 객체 insert) `arrangeXxx`를 추상 훅으로 둔다. 단언과 호출은 계약에 1회만 쓰고 양측이 공유한다.

9필드 매핑 같은 절은 `arrangeAddress(userId, addressId, expected: OrderShippingAddress)`로 받아:
- **Fake측**: expected를 fake에 저장 → fake가 그대로 반환 → 통과(fake의 계약 충실 증명).
- **real측**: expected와 동치인 provider-domain `ShippingAddress`를 fake persistence에 insert → 어댑터가 매핑 → expected와 동등 단언(매핑 정확성 증명).

## 4. 계약 절 매트릭스

| 포트 | 계약 절 | arrange 훅 |
|---|---|---|
| **UserQueryPort** | ①9필드 round-trip 매핑 ②nullable `entranceInfo=null` 보존 ③주소 부재/미소유 시 예외 전파 | `arrangeAddress(u,a,expected)`, `arrangeMissingAddress(u,a)` |
| **PaymentQueryPort** | ①결제 없음→false ②존재+비COMPLETED→false ③COMPLETED→true | `arrangePaid(orderId)`, `arrangeUnpaid(orderId, status?)`, `arrangeNoPayment(orderId)` |
| **LaundromatQueryPort** | ①존재→true ②부재(provider `LaundromatNotFoundException`)→false | `arrangeExisting(id)`, `arrangeMissing(id)` |
| **ServiceAvailabilityQueryPort** | ①가용→no-throw ②area 부재→throw ③시간 불가→throw | `arrangeAvailable(area, pickup, deliver)`, `arrangeMissingArea(area)`, `arrangeUnavailableTime(area, …)` |

## 5. 하이브리드 충실도 (결정 반영)

- **4개 전부 경량**: real provider 서비스(예: `LaundromatQueryService`, `PaymentQueryService`, `ServiceAvailabilityQueryService`, `ShippingAddressService`) + **in-memory fake `*PersistencePort`**. 4개 서비스 모두 생성자 의존성이 `*PersistencePort` 단 하나라 Spring/DB 없이 직접 생성 가능. 예외-as-시그널·매핑을 진짜 서비스 로직으로 검증. (provider측 계약 테스트는 carry-app `src/test`, 순수 JUnit5.)
- **UserQueryPort 추가 스모크 IT 1개**: `UserQueryPortAdapterIntegrationTest : IntegrationTestBase`(real Postgres). 실 JPA로 `ShippingAddress` INSERT → 실 와이어드 어댑터 호출 → 9필드 매핑 단언. 경량 fake-persistence가 못 잡는 **JPA 엔티티/컬럼 매핑 드리프트** 층까지 커버. 스모크 1개만(다른 3 포트는 경량으로 충분, saga IT와 중복 회피).

## 6. 소비자측 Fake 채택 (옵션 B)

Fake 생성·계약검증에 더해, 소비자 단위테스트의 크로스모듈 포트 mock을 검증된 Fake로 교체해 CDC keystone을 실체화한다.

- **마이그레이션 표면(작음)**: `OrderCommandServiceTest`(User·Laundromat·ServiceAvailability 3 포트), `DeliveryCommandServiceTest`(Payment 1 포트). 크로스모듈 포트 stub ~6줄.
- **방식**: `mockk<UserQueryPort>() + every {...}` → `FakeUserQueryPort().apply { arrange... }`. **크로스모듈 포트만 교체**; 같은 모듈 포트(`OrderPersistencePort` 등)·`EventPublisherPort`·`MetricsPort`·`AuditPort`는 mockk 유지(범위 밖).
- **게이트**: 교체 후 `OrderCommandServiceTest`·`DeliveryCommandServiceTest` 전체 GREEN 재확인.

## 7. Gradle 배관 (최소)

- `carry-order/build.gradle.kts`·`carry-delivery/build.gradle.kts`: `java-test-fixtures` 플러그인 추가 + `testFixturesImplementation`(junit5, assertj). testFixtures 소스셋은 main(포트 인터페이스·도메인 vo)을 자동 참조.
- `carry-app/build.gradle.kts`(test): `testImplementation(testFixtures(project(":carry-order")))`, `testImplementation(testFixtures(project(":carry-delivery")))`.
- 소비자 `src/test`가 같은 모듈 testFixtures(Contract 추상·Fake)를 참조하는 것은 기본 제공(자동 의존).

## 8. 네이밍 / 배치 (기존 선례 준수)

기존 `OutboxConnectorContractTest`(carry-infra-kafka) 선례를 따른다.

| 산출물 | 위치 | 패키지 |
|---|---|---|
| `<Port>Contract`(추상), `Fake<Port>` | 소비자 `src/testFixtures` | `...application.port.outbound.contract` |
| `Fake<Port>ContractTest`(소비자측) | 소비자 `src/test` | 동일 |
| `<Port>AdapterContractTest`(provider측) | `carry-app/src/test` | `com.carry.app.contract` |
| `Fake<ProviderPersistence>Port`(경량 IT용) | `carry-app/src/test` | `com.carry.app.contract` 또는 `...test` |
| `UserQueryPortAdapterIntegrationTest`(스모크) | `carry-app/src/test` | `com.carry.app.contract` |

## 9. TDD / teeth 검증

프로덕션 코드는 이미 존재하므로 계약 테스트는 **회귀 가드**다. 계약을 먼저 쓰고 현재 어댑터/서비스에 대해 실행 → GREEN이면 회귀 가드 확보.

**teeth 확인(#90 방식)**: 계약이 실제로 무는지 뮤테이션으로 1회 검증 후 원복:
- `UserQueryPortAdapter`의 매핑 1필드 swap(예: `recipientName`↔`recipientPhone`) → UserQueryPort 계약 RED.
- `LaundromatQueryPortAdapter`의 `catch(LaundromatNotFoundException)` 제거 → Laundromat 계약(부재→false) RED.
- `PaymentQueryService.isOrderPaid`의 `status == COMPLETED` 조건 완화 → Payment 계약 RED.

계약 작성 중 **실제 버그 발견 시에만** 프로덕션 코드 수정(그 외 프로덕션 변경 0 목표).

## 10. 스코프 경계 (YAGNI)

- **포함**: 4 포트 추상 계약 + Fake + 소비자측/​provider측 계약 테스트, UserQueryPort 스모크 IT, 옵션 B 마이그레이션, gradle 배관.
- **제외**: Pact/네트워크 CDC 도구(인프로세스 모놀리스엔 과함), 같은 모듈 persistence 포트 계약(어댑터 봉합점 아님), 소비자 단위테스트의 비-크로스모듈 mock 교체, provider use-case의 미사용 메서드(`findNearby`·`getAllActive` 등) 계약화.

## 11. 검증 게이트

1. `:carry-order:test` · `:carry-delivery:test` · `:carry-app:test` GREEN(경량 + 옵션 B 교체 후).
2. UserQueryPort 스모크 IT GREEN(Testcontainers).
3. teeth 뮤테이션 3건 RED 확인 후 원복.
4. 머지 전 라이브 풀스택 스모크는 본 작업이 **테스트 전용·프로덕션 코드 0**이면 생략 가능(코드 변경 시에만 수행). CI green이 게이트.
