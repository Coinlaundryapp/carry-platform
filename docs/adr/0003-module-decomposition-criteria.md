# ADR-0003: 모듈 분리 기준

## 상태

Accepted

## 날짜

2026-06-09

## 맥락

carry-platform은 24개의 Gradle 서브모듈로 구성된다(`settings.gradle.kts`).
모듈을 "어떤 기준으로 쪼개고, 모듈 간 의존을 어떻게 통제할 것인가"가 구조의 근간이다.

### 문제 정의

단일 모듈(모놀리식 소스 트리)은 경계가 코드 규약에만 의존해 쉽게 침식되고,
반대로 무분별한 모듈 분해는 보일러플레이트·빌드 복잡도를 키운다.
또한 비즈니스 도메인 간 직접 의존이 생기면 결합이 폭발하고 Saga의 느슨한 결합이 무너진다.

### 제약

- 헥사고날 아키텍처를 모듈마다 일관되게 적용(→ [ADR-0001](0001-domain-jpa-separation.md))
- 도메인 간 통신은 동기 직접호출이 아니라 이벤트(Saga, → [ADR-0004](0004-choreography-saga.md))
  또는 명시적 쿼리 포트로만
- 1인 학습/포트폴리오 프로젝트 — 경계 명확성과 패턴 시연 가치가 개발 속도보다 우선

## 결정

**기술 관심사(technical concern)와 비즈니스 도메인(business capability)을 두 축으로 모듈을 분리하고,
도메인 모듈 간 직접 의존을 금지한다. 크로스모듈 통신은 (1) 이벤트 또는 (2) 명시적 쿼리 포트로만 하며,
포트의 실제 결선은 조립 모듈 `carry-app`에서만 일어난다.**

### 핵심 결정 사항

1. **모듈 분류 체계**
   - **공유**: `carry-common`(예외·로깅·메트릭 인터페이스), `carry-event`(도메인 이벤트 정의),
     `carry-audit`(감사 포트). Spring 비의존.
   - **기술 인프라(`carry-infra-*`)**: `persistence`·`kafka`·`redis`·`s3`·`observability`.
     기술 관심사별 수직 분리.
   - **보안**: `carry-security`(JWT·인증 필터·SecurityConfig).
   - **비즈니스 도메인(13개)**: `user`·`laundromat`·`price`·`geo`·`order`·`payment`·`dispatch`·
     `delivery`·`operation`·`review`·`notification`·`media`·`service-availability`.
   - **조립**: `carry-app`(Spring Boot main, 전 모듈 의존, 공통 빈, 크로스모듈 포트 어댑터).
   - **부가**: `carry-loadtest`(격리된 부하 테스트 모듈, 일반 빌드 그래프 비참여).

2. **도메인 모듈 간 직접 의존 금지** — 각 도메인 모듈의 `build.gradle.kts`는 `carry-common`·
   `carry-event`·필요한 `carry-infra-*`만 의존하고, **다른 도메인 모듈을 `project(...)`로 의존하지 않는다.**

3. **크로스모듈 동기 조회는 쿼리 포트로** — 소비자 모듈이 자신의 `application/port/outbound`에
   `UserQueryPort`·`LaundromatQueryPort`·`ServiceAvailabilityQueryPort`(carry-order 정의)·
   `PaymentQueryPort`(carry-delivery 정의)를 정의하고, **구현은 `carry-app`의 `*QueryPortAdapter`가**
   공급자 모듈의 인바운드 UseCase에 위임한다. 소비자는 공급자 모듈의 존재를 모른다.

4. **모듈 내부는 헥사고날 3계층 일관** — 모든 도메인 모듈이 `domain`(모델·VO·예외) /
   `application`(port.inbound·port.outbound·service) / `adapter`(inbound.rest·inbound.kafka·
   outbound.persistence) 구조를 따르며 `HexagonalArchitectureTest`로 강제.

5. **`carry-app`이 유일한 의존 수렴점** — 모든 모듈을 의존·스캔하고, 공통 빈(`ClockConfig`,
   `OpenApiConfig`)과 4개 `*QueryPortAdapter`를 보유. 통합 테스트(Saga IT·계약 테스트·
   `ModuleBoundaryTest`)도 여기서만 실행.

## 결과

### 긍정적

- 비즈니스 경계가 모듈 경계로 물리적으로 드러남 → 변경 영향 범위가 모듈로 국한
- 도메인 간 직접 의존 0 → 결합 폭발 방지, Saga의 느슨한 결합과 일관
- 기술 교체가 `carry-infra-*` 한 곳으로 격리
- 각 모듈 독립 테스트 가능(테스트 픽스처/Fake 포트), 빌드 캐시 효율
- 새 도메인 추가가 기존 도메인을 건드리지 않음(이벤트 구독 + 포트 정의)

### 부정적

- 작은 모듈(`carry-review` 약 15파일, `carry-media` 약 17파일)도 3계층+포트 이중 경계를 강제
  → 단순 CRUD에 비해 보일러플레이트 큼
- 모듈 수(24개)로 빌드 그래프·IDE 탐색 복잡도 증가
- `carry-app`에 크로스모듈 결선이 집중(`*QueryPortAdapter` 유지 비용)

### 위험

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| 과분해(작은 모듈 난립)로 인지 부하 | 중 | 소 | 모듈 과분해 재검토 항목으로 식별(ROADMAP 6.3), 인접 모듈 통합 후보 추적 |
| 도메인 간 우회 의존 유입 | 저 | 고 | `ModuleBoundaryTest`·`HexagonalArchitectureTest`로 경계 강제 |
| 쿼리 포트 남용으로 동기 호출 그래프 복잡화 | 저 | 중 | 상태 변경은 이벤트, 포트는 단순 조회로만 한정 |

## 고려한 대안

### 단일 모듈 + 패키지 경계

하나의 Gradle 모듈 안에서 패키지로만 도메인을 나눔.

**장점:** 빌드 단순, 보일러플레이트 최소, 리팩터링 용이.

**단점:** 경계가 규약에만 의존 → 침식 쉬움. 도메인 간 직접 의존을 컴파일러가 막지 못함.

**기각 이유:** 경계의 강제(compile-time)와 분산 시스템 모듈화 시연이 본 프로젝트의 목표다.

### 도메인별 마이크로서비스(물리적 분리 + 네트워크 경계)

각 도메인을 독립 배포 서비스로.

**장점:** 진짜 독립 배포·확장, 장애 격리.

**단점:** 운영·배포·관측 복잡도 폭증, 1인 프로젝트에 과도. 네트워크 경계가 모든 호출에 비용.

**기각 이유:** "모듈러 모놀리스 + 이벤트 기반 통신"으로 마이크로서비스 분해의 핵심 패턴(경계·
이벤트·포트)을 단일 배포 단위에서 시연하는 편이 학습·운영 비용 대비 효율적.
경계를 모듈로 강제해 두면 이후 서비스 분리도 점진적으로 가능.

## 참조

- `settings.gradle.kts`
- `carry-app/src/main/kotlin/com/carry/app/adapter/` (`UserQueryPortAdapter` 등 4개)
- `carry-order/src/main/kotlin/com/carry/order/application/port/outbound/UserQueryPort.kt`
- `carry-app/src/test/kotlin/.../ModuleBoundaryTest`
- 관련: [ADR-0001 도메인/JPA 분리](0001-domain-jpa-separation.md),
  [ADR-0004 Choreography Saga](0004-choreography-saga.md)
