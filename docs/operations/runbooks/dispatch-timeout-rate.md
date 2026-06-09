# DispatchTimeoutRate

> ✅ **활성** (2026-06-10, PR #97): `DispatchTimeoutSweeper`(@Scheduled) → `timeoutDispatch` 경로가
> `carry_dispatch_timeout_total` 을 발행하면서 본 룰이 활성화됐다. expr 의
> `and sum(rate(...timeout...)) > 0` 가드는 타임아웃 실제 0건일 때의 잡음 오발화를 막는
> 정상 의미론으로 유지된다(아래 "활성화 후 시나리오" = 현행 시나리오).

---

## 개요

- **Alert ID:** `DispatchTimeoutRate`
- **Severity:** warning
- **Route:** business (`#carry-business`)
- **트리거 조건:** 배차 타임아웃율(timeout / accepted+rejected+timeout)이 15분 평균 20% 초과
- **for:** 15m
- **Spec/룰:** [carry-baseline.rules.yml](../../../infra/prometheus/rules/carry-baseline.rules.yml)
- **디자인 문서:** [2026-05-29 Phase 3.4 spec](../../superpowers/specs/2026-05-29-phase-3-4-alert-baseline-design.md)

## 무엇이 일어나고 있는가 (활성화 후 시나리오)

배차 요청 중 20% 이상이 기사가 시간 내에 수락하지 못해 타임아웃 처리됨. 사용자는 "배차 중"
상태가 길어지다 결국 매칭 실패를 받는다. 비즈니스 KPI(배차 성공률·주문 완료율)에 직접 영향.

## 즉시 확인할 것 (5분 이내)

1. **🚧 메트릭 활성화 여부 우선 확인** — `up{job="carry-platform"}` 정상 + 메트릭이
   `actuator/prometheus`에서 노출되는지(`curl localhost:8080/actuator/prometheus | grep carry_dispatch_timeout`)
2. **Grafana 대시보드** `Carry — Business & Resilience`의 배차 funnel 패널에서 timeout 비율 추이 확인
3. **Alertmanager UI** (http://localhost:9093/#/alerts)에서 동시 firing 확인 (`OrderVolumeDropDoD` 동반 여부)
4. **Saga 상관관계**: `correlationId` MDC로 timeout된 dispatch saga 로그 추적.
   `DispatchTimedOut` 이벤트 발행 여부 확인.
5. **지역·시간대 분해** — timeout이 특정 지역에 몰리는지 (활성 기사 풀 부족 가능성).
6. **직전 매칭 알고리즘 변경 이력** — `DispatchAssignmentService` 관련 PR 머지 시점과 timeout spike 시점 비교.

## 흔한 원인 → 대응 (활성화 후 시나리오)

| 원인 | 신호 | 대응 |
|---|---|---|
| 기사 풀 고갈 | 특정 지역의 활성 기사 카운트 0, 해당 지역만 timeout spike | 캠페인·인센티브 발동(긴급 알림). 운영팀에 매뉴얼 매칭 요청 |
| 매칭 알고리즘 회귀 | 직전 배포 시점부터 timeout 비율 spike, 전 지역 균등 발생 | rollback 후 회귀 단위 테스트 추가. `DispatchAssignmentServiceTest` 보강 |
| 푸시 알림 실패 | 알림 인프라(FCM/APNS) 5xx, 기사 앱 미수신 보고 | 알림 채널 점검 + retry 큐 확인. 알림 채널 미러링(SMS fallback) 검토 |

## 에스컬레이션 기준

- 활성화 시 30분 이상 firing 또는 timeout rate > 40%
- `OrderVolumeDropDoD` 동시 firing (전체 비즈니스 영향)
- 특정 지역만 100% timeout (해당 지역 서비스 임시 중단 검토)

## 관련 메트릭/대시보드 패널 (활성화 후)

- Grafana 패널: "Dispatch funnel" 또는 "Dispatch timeout rate"
- 상세 PromQL:
  ```promql
  # 메트릭 도입 후 — 토픽별·지역별 timeout rate
  sum(rate(carry_dispatch_timeout_total[15m]))
    /
  clamp_min(
    sum(rate(carry_dispatch_accepted_total[15m]))
    + sum(rate(carry_dispatch_rejected_total[15m]))
    + sum(rate(carry_dispatch_timeout_total[15m])),
    0.001
  )
  ```
- 진단용:
  ```promql
  # 현재는 absent 가드 발동 — 메트릭 존재 확인
  absent(carry_dispatch_timeout_total)
  ```

## 관련 ROADMAP/known-debts

- known-debts (Phase 3.1): `carry.dispatch.timeout` — 배차 만료 스케줄러 + `Dispatch.timeout()` 호출 도입 시 동반 추가
- known-debts (Phase 3.4): 본 런북의 활성화 트리거 (이번 PR에 명시)
- ROADMAP Phase 5 멱등성·동시성 테스트 확장과 함께 dispatch 스케줄러 작업 가능
