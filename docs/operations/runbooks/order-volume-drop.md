# OrderVolumeDropDoD

## 개요

- **Alert ID:** `OrderVolumeDropDoD`
- **Severity:** warning
- **Route:** business (`#carry-business`)
- **트리거 조건:** 1시간 평균 주문 생성률이 24시간 전 동시간 대비 50% 이하 (30분 지속)
- **for:** 30m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가

주문 생성 rate가 어제 같은 시각 대비 절반 이하로 30분 이상 지속. 시스템 측 장애일 수도, 외부
요인(앱 스토어 리뷰 폭락·캠페인 종료·외부 SNS 채널 이슈)일 수도 있다. 비즈니스 KPI 알럿이므로
즉시 critical은 아니지만 24시간 누적 시 매출 영향이 큼.

> ℹ️ 룰에는 `and sum(rate(... offset 1d)) > 0.01` 가드가 있어 어제 자정·새벽 등 자연
> trough 시간대에는 false positive로 firing하지 않는다.

## 즉시 확인할 것 (5분 이내)

1. **Grafana 대시보드** `Carry — Business & Resilience`의 "주문 생성률(DoD)" 패널 확인.
   오늘 vs 어제 같은 시각 비교 추이.
2. **Alertmanager UI**에서 동시 firing 알럿 확인.
   `ApiHighErrorRate`·`PaymentHighFailureRate` 동반 여부 (시스템 장애 신호).
3. **Saga 상관관계**: `correlationId` MDC로 직전 1시간 주문 생성 saga 로그 추적.
   `OrderCreatedEvent` 발행 빈도 확인.
4. **앱·웹 헬스체크** — carry-app(모바일) 및 web의 외부 헬스체크 결과 확인.
   stores 리뷰 폭락 또는 앱 자체 에러 신호 확인.
5. **비즈니스 캠페인 일정 확인** — 마케팅 측에 직전 종료된 캠페인 또는 외부 이벤트(공휴일 등) 문의.
6. **트래픽 source 분해** — Google Analytics 또는 자체 acquisition 추적에서 채널별 drop 확인.

## 흔한 원인 → 대응

| 원인 | 신호 | 대응 |
|---|---|---|
| 앱·웹 장애 | `ApiHighErrorRate` 동시 firing, 5xx 비율 상승, 앱 크래시 보고 | 카나리/롤백 즉시 검토. 이미 다른 알럿이 firing이면 그쪽 우선 대응(주문 drop은 결과) |
| 결제 funnel drop | `PaymentHighFailureRate` 동시 firing, 결제 단계에서 이탈 | 결제 alert 우선 critical 대응 후 자연 회복 대기. 사용자 안내(결제 시스템 점검 중) |
| 외부 SNS·검색 채널 이슈 | 다른 알럿 무 firing, 시스템 메트릭 정상, 특정 acquisition 채널만 drop | 마케팅·CS와 공유. 채널별 PR 별도 대응. 운영 측 신호로만 활용 |

## 에스컬레이션 기준

- 1시간 이상 지속 또는 < 30% drop (전일 대비 70%+ 감소)
- `ApiHighErrorRate` 또는 `PaymentHighFailureRate` 동시 firing → 시스템 측 우선 대응
- 24시간 누적 매출 영향 추정 시 비즈니스 보고

## 관련 메트릭/대시보드 패널

- Grafana 패널: "주문 생성률(DoD 비교)", "주문 라이프사이클 funnel"
- 상세 PromQL:
  ```promql
  # DoD 비율 — 본 룰과 동일 expression
  sum(rate(carry_order_created_total[1h]))
    /
  clamp_min(
    sum(rate(carry_order_created_total[1h] offset 1d)),
    0.001
  )
  ```
- 진단:
  ```promql
  # 시간대별 trough/peak 식별 — 자연 변동인지
  sum(increase(carry_order_created_total[1h]))

  # 어제 같은 시각 절대값
  sum(increase(carry_order_created_total[1h] offset 1d))
  ```

## 관련 ROADMAP/known-debts

- ROADMAP Phase 3.1 비즈니스 메트릭 카탈로그 (PR #63) — `carry.order.created` 카운터
- ROADMAP Phase 3.2 Grafana 대시보드 (PR #64) — 주문 라이프사이클 funnel 패널
- known-debts: `carry.saga.duration` (주문→배달 wall-clock) 추가 시 funnel 정밀도 향상
- ROADMAP Phase 4 보안 (인증 만료가 회원가입·로그인 funnel을 막을 가능성)
