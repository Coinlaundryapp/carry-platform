# 성능 회귀 테스트 설계 (ROADMAP 5.4)

> 작성일: 2026-06-09 · 이슈 #94 · base develop
> 백로그 P2 #7 · ROADMAP 5.4 "성능 회귀 테스트 — 주요 API p95 기준선"

## 1. 목적과 범위

주요 API의 성능 회귀를 자동 검출한다. carry는 교육용 + 포트폴리오 프로젝트이므로 "실무에서 실제로 쓰는 구성"을 따른다.

실무에서 성능 회귀 테스트는 성격이 다른 두 축으로 분리되어 운용된다. 이 둘을 한 게이트에 섞으면 부하 측정의 환경 변동이 CI를 flaky하게 만든다.

| | Tier 1 | Tier 2 |
|---|---|---|
| 측정 대상 | SQL 실행 *횟수* | 응답시간 p50/p95/p99, 처리량 |
| 실행 위치 | 매 PR CI 게이트 | 온디맨드 / nightly (게이트 아님) |
| 결정성 | 머신 독립적, flaky 0 | 머신 종속 → 게이트 부적합 |
| 잡는 회귀 | N+1 / 쿼리 폭증 | 절대 지연 회귀 |

**대상 API (백로그 명시)**: 주문 생성, 커서 기반 목록 조회, 배차 수락.

**비범위**:
- 명령측 idempotency-key (인프라 부재, 백로그 #5에서도 범위 밖)
- dev/prod 실부하 인프라 (클라우드 미준비)
- 마이크로서비스 분리 후의 Chaos/Canary (docs/08-testing.md의 미래 항목)

## 2. Tier 1 — 결정적 쿼리 회귀 가드

### 2.1 원리

성능 회귀의 실무 최빈 원인은 N+1과 쿼리 폭증이다. 응답시간(ms)은 CI 러너 속도에 종속되어 절대 임계값이 flaky하지만, **SQL 실행 횟수는 코드 구조의 함수라 머신 독립적**이다. 따라서 횟수를 단언하면 결정적 게이트가 된다.

### 2.2 도구: datasource-proxy

`net.ttddyy:datasource-proxy`로 실제 `DataSource`를 프록시하여 JDBC 레벨에서 실행된 모든 쿼리를 캡처한다. `QueryCountHolder.getGrandTotal()` / `getQueryCountMap()`으로 SELECT/INSERT/UPDATE/DELETE 횟수를 조회·단언한다.

- **Hibernate Statistics 대비 선택 이유**: JDBC 레벨이라 native query·cursor 페이지네이션 쿼리까지 빠짐없이 캡처. JPA 우회 경로도 포착.
- **적용 범위**: test 전용. 프로덕션 `DataSource` 구성은 변경하지 않는다. test profile에서만 `ProxyDataSourceBuilder`로 감싸거나, 테스트 설정에서 `BeanPostProcessor`로 래핑한다.

### 2.3 위치와 구조

- `carry-app` 의 test 소스셋. `IntegrationTestBase`(Testcontainers PostgreSQL) 상속.
- 쿼리 카운트 측정을 위한 test 설정 클래스(`QueryCountTestConfig`)에서 `DataSource`를 `ProxyDataSource`로 래핑.
- 각 테스트: `QueryCountHolder.clear()` → 대상 서비스/엔드포인트 1회 실행 → 카운트 단언.
- 기존 `:carry-app:test`에 포함되어 매 PR CI에서 자동 실행. 별도 워크플로우 불필요.

### 2.4 대상 시나리오와 단언

1. **커서 목록 조회 (핵심)**: K건(예: 20건)의 주문/배차를 조회할 때 실행 SELECT 수가 K에 비례하지 않음을 단언. 고정 상한(예: `<= 3`)으로 N+1 부재를 보장. 데이터를 10건/20건으로 늘려도 쿼리 수가 동일함을 검증하는 형태가 가장 강력.
2. **주문 생성**: 단일 주문 생성 시 write 경로의 INSERT/SELECT 횟수 상한 단언.
3. **배차 수락**: 단일 수락 시 쿼리 횟수 상한 단언.

상한값은 "현재 측정값 + 작은 여유"로 설정해 의도적 증가는 통과시키되 우발적 폭증을 차단한다. 각 상한의 근거(현재 실측치)를 테스트 주석 또는 spec에 기록.

### 2.5 teeth (뮤테이션 검증)

가드가 실제로 회귀를 잡는지 확인하기 위해, 구현 후 의도적으로 N+1을 유발(예: 목록 조회에서 연관 엔티티를 루프 내 lazy 접근)하여 테스트가 RED가 되는지 확인한 뒤 원복한다.

## 3. Tier 2 — Gatling 부하·p95 측정

### 3.1 도구와 격리

- `gatling-gradle-plugin` + Gatling **Java DSL**(Kotlin에서 호출 가능).
- 별도 격리: 전용 소스셋(`src/gatling`) 또는 전용 모듈(`carry-loadtest`). 프로덕션·기존 테스트 빌드 그래프에 영향 0. 부하 의존성이 일반 빌드로 새지 않도록 한다.

### 3.2 대상 환경

기존 "라이브 풀스택 스모크" 워크플로우를 재사용한다.

- docker: postgres(5442) + redis + kafka
- `bootRun` 기동, `MANAGEMENT_TRACING_ENABLED=false` (otel exporter가 요청을 블록하는 기존 함정 회피)
- Gatling이 `localhost:8080`을 외부에서 부하

Testcontainers in-process 부하가 아닌 **실부팅 외부 부하**를 택한 이유: 같은 JVM에서 부하를 걸면 측정이 오염된다. 외부 부하가 실무 부하테스트의 표준이며 기존 스모크 절차와도 일치.

### 3.3 시나리오

1. 인증: 로그인 → access 토큰 확보 (이후 요청 헤더에 사용)
2. 주문 생성: `POST /api/v2/orders` (실 엔드포인트 경로는 구현 시 확인)
3. 커서 목록 조회: 주문/배차 목록 cursor 페이지네이션
4. 배차 수락: carrier 토큰으로 수락

각 시나리오에 ramp-up 사용자 부하를 주고 p50/p95/p99 + 처리량을 측정.

### 3.4 기준선과 assertion

- 최초 측정값을 이 spec 또는 README에 **기준선**으로 기록(머신 사양 명시).
- Gatling `assertions`로 느슨한 회귀만 검출: 예 `global.responseTime.percentile3().lt(기준선_p95 * 1.5)`, 실패율 `< 1%`.
- 절대 수치를 빡빡하게 걸지 않는다 — 머신 변동 흡수, 명백한 회귀만 RED.

### 3.5 산출물

- Gatling HTML 리포트 (포트폴리오 가치)
- 기준선 문서 + 실행 가이드(README): docker 풀스택 기동 → bootRun → `./gradlew :carry-loadtest:gatlingRun` 순서

### 3.6 실행 트리거

- 로컬 수동 실행이 기본.
- 선택적으로 `workflow_dispatch` GitHub Actions job 추가(매 PR 게이트 아님). 클라우드 미준비라 실배포 대상은 없으므로 CI 러너 내 docker compose 위에서 스모크 수준으로만.

## 4. 완료 기준

- Tier1 쿼리 가드 IT가 N+1 회귀를 RED로 검출(뮤테이션으로 teeth 확인 후 원복).
- Tier1이 기존 `:carry-app:test`에 포함되어 매 PR GREEN.
- Tier2 Gatling 시뮬레이션이 3개 시나리오의 p95를 측정하고 기준선이 문서화됨.
- Tier2가 격리된 소스셋/모듈이라 일반 빌드·테스트에 영향 0.
- 전체 `compileTestKotlin` + 영향 모듈 `:test` + `:carry-app:test` GREEN + 라이브 풀스택 스모크(Gatling 실행) 통과.
- 프로덕션 코드 변경 최소화(가능하면 0; 측정은 test/loadtest 소스셋에 격리).

## 5. 리스크와 완화

| 리스크 | 완화 |
|---|---|
| Tier1 쿼리 상한이 너무 빡빡해 정당한 변경에도 RED | 상한 = 실측 + 여유, 근거 기록. 실패 시 의도성 검토 후 조정 |
| Tier2 Gatling이 빌드를 무겁게 | 전용 소스셋/모듈로 격리, 일반 빌드 의존성에서 제외 |
| Gatling Kotlin 미지원 마찰 | Java DSL 사용(공식 지원), 시나리오는 Java로 작성 가능 |
| 부하 시 otel exporter 블록(기존 함정) | `MANAGEMENT_TRACING_ENABLED=false`, 필요시 `-Dotel.sdk.disabled=true` |
| datasource-proxy가 프로덕션 경로 오염 | test profile/소스셋 한정 래핑, 프로덕션 DataSource 무변경 |

## 6. 참고

- 백로그: `2026-06-07-carry-remaining-backlog.md` P2 #7
- 테스트 전략: `docs/08-testing.md` (Load Test = k6/Gatling, 마이크로서비스 분리 후 항목 → 현 단계에 맞게 모놀리스 로컬 부하로 선반영)
- 기존 IT 베이스: `carry-app/src/test/kotlin/com/carry/app/test/IntegrationTestBase.kt`
- 라이브 스모크 절차: 백로그 운영 메모
