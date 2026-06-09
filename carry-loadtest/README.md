# carry-loadtest — Gatling 부하·p95 측정 (Tier2, 온디맨드)

성능 회귀 테스트의 **Tier2**. 격리된 Gradle 모듈에서 Gatling Java DSL로 로컬 docker
풀스택을 외부 부하하여 주요 API의 p50/p95/p99를 측정한다.

> **CI 게이트가 아니다.** 머신 사양·네트워크에 따라 절대 수치가 달라지므로 매 PR에서
> 자동 실행하지 않는다. 결정적 회귀 가드는 Tier1(`:carry-app:test`의
> `QueryCountGuardTest`)이 담당한다. 이 모듈은 `gatlingRun`으로만 실행되며
> 일반 빌드/테스트 그래프(`:carry-app:test`, `check`)에 끌려오지 않는다.

## 시나리오

| # | 시나리오 | 엔드포인트 | 토큰(role) | 부하 |
|---|----------|------------|------------|------|
| ① | 주문 목록 조회 | `GET /api/v2/orders/my?size=20` | CUSTOMER(1) | rampUsers(50) / 30s |
| ② | 주문 생성 | `POST /api/v2/orders` | CUSTOMER(1) | rampUsers(30) / 30s |
| ③ | 배차 선점 | `GET /api/v2/dispatches/available` → `POST /api/v2/dispatches/{id}/claim` | CARRIER(2) | rampUsers(20) / 30s |

시나리오 ③은 PENDING 배차 풀(시드 30개)을 소비한다. carrier 토큰이 단일(userId=2)이라
동시 claim이 같은 행을 노릴 수 있어 `claim` 응답을 `status in (200, 409)`로 허용한다
(409 = 이미 다른 가상유저가 선점). 풀(30) > 사용자(20)로 두어 대부분 200이 되도록 한다.

## 실행 순서

JWT secret/프로파일 일치가 핵심이다. bootRun은 `local` 프로파일을 써야 datasource URL과
`jwt.secret`이 바인딩된다(default `application.yml`엔 jwt.secret 없음). `local`의 secret은
`carry-local-dev-secret-key-do-not-use-in-production`이므로 Gatling의 `JWT_SECRET`도
**동일 값**이어야 토큰이 검증된다(불일치 시 전 요청 401 → 실패율 100%).

```bash
# 0. JDK 21 (머신 기본 Java 25는 Gradle 8.12.1 비호환)
export JAVA_HOME="C:/Users/Eisen/.jdks/ms-21.0.7"

# 1. docker 풀스택 (postgres 5432 + redis 6379 + kafka 3-node)
docker compose up -d postgres redis kafka-1 kafka-2 kafka-3

# 2. 앱 기동 — local 프로파일이 ddl-auto:update로 스키마를 생성한다.
#    (flyway off이므로 스키마는 bootRun이 만든다 → seed.sql은 기동 이후에 적용)
SPRING_PROFILES_ACTIVE=local MANAGEMENT_TRACING_ENABLED=false ./gradlew :carry-app:bootRun
# GET http://localhost:8080/actuator/health 가 {"status":"UP"} 될 때까지 대기
# 8080이 다른 프로세스에 점유돼 있으면 SERVER_PORT=8081 등으로 바꾸고 아래 -DbaseUrl 함께 변경.

# 3. 시드 적용 (스키마 생성 이후!) — claim 시나리오는 PENDING 풀을 소비하므로
#    재실행 전 매번 다시 적용해 풀(30)을 채운다.
docker exec -i carry-postgres psql -U carry -d carry < carry-loadtest/src/gatling/resources/seed.sql

# 4. Gatling 실행 — local secret을 JWT_SECRET으로 주입
JWT_SECRET=carry-local-dev-secret-key-do-not-use-in-production ./gradlew :carry-loadtest:gatlingRun
# 비표준 포트로 띄웠다면: ... ./gradlew :carry-loadtest:gatlingRun -DbaseUrl=http://localhost:8081

# 5. 정리
docker compose down
```

리포트: `carry-loadtest/build/reports/gatling/<simulation>-<timestamp>/index.html`

> **seed.sql 주의(local/Hibernate 스키마)**: local 프로파일은 flyway off + ddl-auto:update라
> 스키마를 Hibernate가 만든다. Hibernate-DDL에는 마이그레이션의 `DEFAULT now()`가 없어
> `created_at`/`updated_at`이 NOT-NULL-without-default다 → seed.sql은 이 컬럼을 명시한다.
> 또 `service_area_schedules`엔 (service_area_id, day_of_week) UNIQUE가 Hibernate-DDL에
> 생성되지 않아 `ON CONFLICT` 대신 `WHERE NOT EXISTS`로 멱등 처리했다.

## 환경변수 / 시스템 프로퍼티

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `JWT_SECRET` | `test-secret-key-for-integration-tests` | 서버 secret과 동일해야 함. 부하 실행 시 local secret으로 override 필수 |
| `-DbaseUrl` | `http://localhost:8080` | 대상 base URL |

## 호환 버전

- `io.gatling.gradle` plugin **3.13.5** — Gradle 8.12.1 / JDK 21에서 정상 resolve 확인
  (`:carry-loadtest:tasks`에 `gatlingRun` 노출, BUILD SUCCESSFUL). 별도 핀 불필요.

## assertion 정책

- `global().responseTime().percentile3()`(p95) `< BASELINE_P95_MS`
- `global().failedRequests().percent()` `< 1.0%`

`BASELINE_P95_MS`는 `CarryLoadSimulation.java`의 상수다. **단일 실행 × 1.5**가 원래 의도였으나,
공유 dev 머신에서 글로벌 p95가 실행마다 55~303ms로 크게 흔들려(아래 변동성 메모) 단일 실행에
과적합하면 부서지기 쉽다. 따라서 **최악 관측치(303ms) + 여유 → 500ms**로 설정했다.
진짜 회귀(N+1 재유입, 인덱스 누락 등)는 p95를 초 단위로 밀어올리므로 이 임계로도 검출 가능하다.
전용 측정 환경(고정 머신, 격리)에서는 `측정 p95 × 1.5`로 더 타이트하게 재설정하기를 권장한다.

## 측정 기준선 (p50 / p95 / p99)

측정 일자: **2026-06-09**
측정 머신: **Intel Core Ultra 5 125H (18 logical) / 31.6 GB RAM / Windows 11 Pro**, 로컬 docker 풀스택.
부하: 120 요청 (목록 50 + 생성 30 + 선점 20), 각 시나리오 30초 ramp. **실패율 0%.**

아래 표는 GREEN 확정 실행(최종 4회차) 기준이다.

| 시나리오 | p50 (ms) | p95 (ms) | p99 (ms) | 실패율 |
|----------|----------|----------|----------|--------|
| ① 주문 목록 조회 (`GET /orders/my`) | 28 | 41 | 68 | 0% |
| ② 주문 생성 (`POST /orders`) | 75 | 100 | 121 | 0% |
| ③-a 배차 목록 (`GET /dispatches/available`) | 31 | 67 | 67 | 0% |
| ③-b 배차 선점 (`POST /dispatches/{id}/claim`) | 38 | 88 | 88 | 0% |
| **global** | **43** | **84** | **100** | **0%** |

### 변동성 메모 (왜 ×1.5가 아니라 500ms인가)

같은 시드/부하로 4회 측정한 글로벌 p95:

| 회차 | global p95 (ms) | 실패율 |
|------|-----------------|--------|
| 1 | 130 | 0% |
| 2 | 303 | 0% |
| 3 | 55 | 0% |
| 4 (확정) | 84 | 0% |

JIT 워밍업·GC·동시 실행 중인 무관 컨테이너(머신 공유) 영향으로 단일 실행 p95가 5배 이상
흔들린다. 절대 수치는 **CI 게이트가 아니라 참고용**이며, 결정적 회귀 검출은 Tier1이 담당한다.
③ 선점 시나리오는 시드 풀(30) > 사용자(20)라 4회 모두 409 없이 200으로 완료됐다.
