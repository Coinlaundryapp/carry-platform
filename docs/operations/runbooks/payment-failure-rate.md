# PaymentHighFailureRate

## ⚠ CRITICAL — 즉시 대응 필요 ⚠

본 알럿은 **critical** 라벨로 `oncall` 라우트(PagerDuty/Opsgenie + `#carry-incidents`)에
발신된다. 사용자의 결제가 직접 실패하므로 매출 및 신뢰도에 즉시 영향. 5분 firing이면
자동 on-call 호출되도록 후속 PR로 연동 예정 (현재 placeholder).

---

## 개요

- **Alert ID:** `PaymentHighFailureRate`
- **Severity:** **critical**
- **Route:** **oncall** (PagerDuty/Opsgenie — 후속 PR로 연결)
- **트리거 조건:** 결제 실패율(failure / success+failure)이 5분 평균 5% 초과
- **for:** 5m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

결제 시도 중 5% 이상이 실패 상태로 종료됨. 사용자 측에서는 결제 완료 화면을 보지 못하고
재시도하거나 이탈한다. PG 전체 장애 시 100%까지 치솟을 수 있고, 다중 PG 환경에서는 한 PG의
부분 장애 신호일 수 있다.

## 즉시 확인할 것 (5분 이내)

1. **🚨 즉시 온콜 호출** — PagerDuty/Opsgenie 자동 호출 (현재 placeholder, 후속 PR로 연결).
   `#carry-incidents`에 alert 메시지 동시 발신.
2. **Grafana 대시보드** `Carry — Business & Resilience`의 "결제 성공/실패" 패널 확인.
   실패 시작 시점과 PG 라벨별 분포 식별.
3. **Alertmanager UI**에서 동시 firing 확인. `ApiHighErrorRate`·`KafkaConsumerLag` 동반 여부.
4. **PG 외부 상태 페이지** — Toss/Naver/Kakao Pay 등 사용 중인 PG의 외부 상태 페이지 직접 확인
   (`status.tosspayments.com` 등).
5. **Saga 상관관계**: `correlationId` MDC로 실패 결제의 saga 로그 추적.
   `PaymentFailedEvent` 발행 흐름과 PG 응답 코드 확인.
6. **PG별 분해**: `by(pg)` PromQL로 어느 PG가 실패의 원천인지 즉시 식별.
   특정 PG만이면 해당 PG를 일시 비활성화하고 다른 PG로 라우팅 검토.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| PG 장애 | 특정 `pg` 라벨에서 failure spike, `resilience4j_circuitbreaker_state{name=~"pg-gateway.*"}` OPEN | Phase 2.2 CB 자연 fallback 대기. 다중 PG 환경이면 트래픽을 정상 PG로 우회 (env 설정) |
| PG API 키 만료 | 401·403 응답이 5xx로 변환되어 spike, 특정 PG 100% 실패 | 환경변수(`PG_TOSS_API_KEY` 등) 갱신 후 컨테이너 재시작. 키 만료 모니터링 알럿 추가 |
| 결제 금액 한도 초과 | 4xx 응답 비율 5%+ (한도 정책 차단), 특정 사용자 그룹 집중 | 한도 정책 재검토. 우회 결제 채널 안내 또는 한도 임시 조정 |

## 에스컬레이션 기준

- **즉시** — critical은 발생 즉시 on-call 호출 트리거
- 5분 firing 이상 → automatic page (후속 PR 연결 후)
- 10분 이상 또는 모든 PG 동시 실패 → 결제 모듈 일시 비활성화 검토 (사용자에게 "결제 시스템 점검 중" 안내)

## 관련 메트릭/대시보드 패널

- Grafana 패널: "결제 성공/실패", "PG별 응답시간"
- 상세 PromQL:
  ```promql
  # PG별 실패율 — 어느 PG가 문제인지
  sum by(pg) (rate(carry_payment_failure_total[5m]))
    /
  clamp_min(
    sum by(pg) (rate(carry_payment_success_total[5m]))
    + sum by(pg) (rate(carry_payment_failure_total[5m])),
    0.001
  )
  ```
- 진단:
  ```promql
  # PG CB 상태 — OPEN 여부
  resilience4j_circuitbreaker_state{name=~"pg-gateway.*", state="open"}
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 2.2 PG Circuit Breaker (PR #61)
- known-debts: PG_GATEWAY_UNAVAILABLE fallback 큐 (사용자 재시도 자동화)
- 본 알럿 활성화는 실 Slack/PagerDuty 연결 후속 PR 필요 (known-debts에 명시)
