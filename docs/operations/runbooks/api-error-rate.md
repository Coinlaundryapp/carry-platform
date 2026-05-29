# ApiHighErrorRate

## 개요

- **Alert ID:** `ApiHighErrorRate`
- **Severity:** warning
- **Route:** slack (`#carry-alerts`)
- **트리거 조건:** 5xx 에러율이 5분 평균 5% 초과
- **for:** 5m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

전체 API 트래픽 대비 HTTP 5xx 응답 비율이 5%를 5분 이상 초과. 사용자의 약 5% 이상이 서버 측
실패를 경험 중. 클라이언트 측 재시도가 트래픽을 더 증폭시켜 cascading failure로 갈 수 있는
초기 신호.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience`에서 "5xx 에러율" 및 status 분포 패널 확인
2. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 알럿 확인.
   `HikariPoolSaturation`·`ApiHighLatencyP99`·`PaymentHighFailureRate` 동반 여부.
3. **Saga 상관관계**: `correlationId` MDC와 GlobalExceptionHandler의 ERROR 로그(Phase 3.3 분기)로
   실패 패턴 추적
4. **최근 PR 머지 이력** — `git log --merges origin/develop --since='1 hour ago'`로 직전 배포 식별.
   배포 시점부터 5xx 폭증이면 회귀 가능성.
5. **에러 분포 분해** — 어떤 URI/status code에 집중되는지 PromQL로 분해.
   특정 엔드포인트 1개라면 그 모듈 좁혀서 진단.
6. **kafka-connect/외부 인증 상태** — `actuator/health` 또는 외부 의존성 헬스체크 확인.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| 배포 회귀 | 직전 commit 머지 시점부터 5xx 폭증, 특정 URI에 집중 | 즉시 rollback (`git revert <SHA>` 또는 카나리 전환). 회귀 모듈에 단위 테스트 추가 |
| DB connection 고갈 | `HikariPoolSaturation` 동시 firing, `OptimisticLockingFailureException` 다발 | `HIKARI_POOL_SIZE` env 증가(현재 20). 트랜잭션 길이 점검(불필요한 외부 API 호출이 내부에 있는지) |
| 외부 인증 만료 | 401·403 + 5xx 혼재, 특정 외부 호출에서 spike | 토큰 갱신(naver/kakao/PG API key). 만료 모니터링 알럿 추가 검토 |

## 에스컬레이션 기준

- 5xx > 10% 또는 `/api/v1/payments/**` 5xx 발생 (결제 영향)
- 30분 이상 지속
- `PaymentHighFailureRate` critical 동시 firing → critical 우선 대응

## 관련 메트릭/대시보드 패널

- Grafana 패널: "5xx 에러율", "HTTP status 분포"
- 상세 PromQL:
  ```promql
  # URI별·status별 분해 — 어디서 무엇이 실패하는지
  sum by(uri, status) (
    rate(http_server_requests_seconds_count{status=~"5.."}[5m])
  )
  ```
- 비즈니스 예외 vs 시스템 예외 구분:
  ```promql
  # 전체 5xx 추세 (BusinessException 5xx + 일반 RuntimeException 합)
  sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 3.4 (본 알럿 기준선)
- Phase 1.1 BusinessException 통일 (PR #55) — 비비즈니스 4xx/5xx 레벨 정책
- known-debts: 비비즈니스 4xx 레벨 정책 재검토 (Phase 3.3 후속)
