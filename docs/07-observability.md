# 07. Observability, 서비스 메시, 카나리 배포

> 최종 수정일: 2026-03-11 (초안) · 상태 갱신: 2026-06-13
> 상태: **혼합 — Observability는 구현됨, 서비스 메시는 미채택**

> ⚠️ **두 부분의 상태가 다르다:**
> - **Observability(OTel·Prometheus·Grafana·알럿)는 구현·운영된다** — 실제 스택은 [12-observability-stack.md](12-observability-stack.md),
>   메트릭 카탈로그는 [11-business-metrics.md](11-business-metrics.md), 로깅은 [13-logging-policy.md](13-logging-policy.md)가 사실원.
> - **아래 "서비스 메시 — Linkerd"·카나리 배포 절은 미채택**(ADR-0007 모듈러 모놀리스 유지, 클라우드 미준비). 구상으로만 보존.

---

## Observability 전략 개요

관측 가능성의 세 기둥(Three Pillars)을 모두 구현한다:

| 기둥 | 도구 | 목적 |
|------|------|------|
| **Traces** | OpenTelemetry → Jaeger | 요청 흐름 추적, Saga 시각화 |
| **Metrics** | OpenTelemetry → Prometheus | 성능 지표, SLI/SLO, 카나리 평가 |
| **Logs** | 구조화 로깅 → (향후 Loki) | 디버깅, 에러 분석 |

---

## OpenTelemetry 아키텍처

```
┌─────────────────────┐
│   Carry App         │
│   (OTel SDK auto-   │
│    instrumentation)  │
│                     │
│  Spring MVC ────────┤
│  JPA/Hibernate ─────┤──── OTLP (gRPC :4317) ────→ ┌─────────────────┐
│  Kafka Producer ────┤                              │  OTel Collector │
│  Kafka Consumer ────┤                              │                 │
│  HTTP Client ───────┤                              │  receivers:     │
└─────────────────────┘                              │    otlp         │
                                                     │  processors:    │
                                                     │    batch        │
                                                     │  exporters:     │
                                                     │    jaeger       │
                                                     │    prometheus   │
                                                     └────────┬────────┘
                                                              │
                                                 ┌────────────┼────────────┐
                                                 ▼            ▼            ▼
                                            ┌─────────┐ ┌──────────┐ ┌─────────┐
                                            │ Jaeger  │ │Prometheus│ │  (Loki) │
                                            │ :16686  │ │  :9090   │ │  향후    │
                                            └─────────┘ └──────────┘ └─────────┘
```

### OTel Collector 설정 (otel-collector-config.yml)

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s
    send_batch_size: 1024

exporters:
  otlp/jaeger:
    endpoint: jaeger:4317
    tls:
      insecure: true
  prometheus:
    endpoint: 0.0.0.0:8889

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlp/jaeger]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

---

## Trace Context 전파

### HTTP → Kafka → HTTP 전파

Kafka 메시지 헤더에 W3C TraceContext를 포함시켜, 모듈 간 이벤트 흐름을 하나의 트레이스로 추적한다.

```
주문 생성 API 요청 (trace-id: abc123)
  → Order Module (span: order.create)
    → Outbox INSERT
      → Debezium → Kafka (header: traceparent=abc123)
        → Payment Module (span: payment.process, same trace-id)
          → Outbox INSERT
            → Debezium → Kafka (header: traceparent=abc123)
              → Dispatch Module (span: dispatch.create, same trace-id)
```

Jaeger UI에서 하나의 trace-id로 주문 생성 → 결제 → 배차의 전체 Saga 흐름을 시각화할 수 있다.

### Outbox에 TraceContext 저장

Debezium CDC를 거치면 원래의 trace context가 유실될 수 있다.
이를 방지하기 위해 Outbox 테이블에 trace_id를 함께 저장한다:

```kotlin
outboxRepository.save(
    OutboxEvent(
        aggregateType = "Order",
        aggregateId = order.id.toString(),
        eventType = "OrderCreated",
        payload = objectMapper.writeValueAsString(event),
        traceId = Span.current().spanContext.traceId  // 현재 trace-id 저장
    )
)
```

Consumer 측에서 수신한 이벤트의 traceId로 새 span을 생성하면, Saga 전체가 하나의 트레이스로 연결된다.

---

## 핵심 메트릭

| 메트릭 | 설명 | 용도 |
|--------|------|------|
| `http.server.requests` | API 요청 레이턴시, 상태 코드 | SLI, 카나리 평가 |
| `order.created.count` | 주문 생성 수 | 비즈니스 KPI |
| `saga.step.duration` | Saga 단계별 소요 시간 | 병목 감지 |
| `kafka.consumer.lag` | 이벤트 소비 지연 | 처리 지연 감지 |
| `outbox.pending.count` | 미발행 Outbox 이벤트 수 | CDC 장애 감지 |
| `jvm.threads.virtual.count` | Virtual Thread 수 | 리소스 모니터링 |

---

## 서비스 메시 — Linkerd

### 왜 서비스 메시인가

마이크로서비스로 분리 후, 서비스 간 통신에 다음이 필요하다:
- **mTLS**: 서비스 간 암호화 통신 (zero-trust)
- **트래픽 메트릭**: 서비스 간 성공률, 레이턴시 자동 수집
- **리트라이/타임아웃**: 인프라 레벨 복원력
- **트래픽 분할**: 카나리 배포를 위한 가중치 기반 라우팅

### Linkerd 아키텍처

```
┌─────────────────────────────────────────────┐
│                Kubernetes Cluster            │
│                                             │
│  ┌─────────────┐     ┌─────────────┐       │
│  │ order-svc   │     │ payment-svc │       │
│  │ ┌─────────┐ │     │ ┌─────────┐ │       │
│  │ │ linkerd │◄├─mTLS─┤►│ linkerd │ │       │
│  │ │ proxy   │ │     │ │ proxy   │ │       │
│  │ └─────────┘ │     │ └─────────┘ │       │
│  │ ┌─────────┐ │     │ ┌─────────┐ │       │
│  │ │   app   │ │     │ │   app   │ │       │
│  │ └─────────┘ │     │ └─────────┘ │       │
│  └─────────────┘     └─────────────┘       │
│         │                    │               │
│         ▼                    ▼               │
│  ┌──────────────────────────────────┐       │
│  │      Linkerd Control Plane       │       │
│  │  ┌──────────┐  ┌──────────────┐  │       │
│  │  │ identity │  │ destination  │  │       │
│  │  │ (mTLS CA)│  │ (service    │  │       │
│  │  │          │  │  discovery) │  │       │
│  │  └──────────┘  └──────────────┘  │       │
│  └──────────────────────────────────┘       │
│                                             │
│  ┌──────────────────────────────────┐       │
│  │       Linkerd Viz Extension      │       │
│  │  (Prometheus + Grafana 대시보드)   │       │
│  └──────────────────────────────────┘       │
└─────────────────────────────────────────────┘
```

### Linkerd가 제공하는 것

| 기능 | 설명 |
|------|------|
| **자동 mTLS** | Pod 간 모든 TCP 통신을 자동 암호화 |
| **골든 메트릭** | 요청 수, 성공률, 레이턴시 (p50/p95/p99) 자동 수집 |
| **리트라이** | ServiceProfile 기반 경로별 리트라이 설정 |
| **타임아웃** | 경로별 타임아웃 설정 |
| **트래픽 분할** | TrafficSplit CRD로 가중치 기반 라우팅 |

### Linkerd 메트릭 → OTel Collector 연동

Linkerd Viz의 Prometheus를 OTel Collector의 데이터 소스로 활용하여,
애플리케이션 메트릭과 인프라 메트릭을 하나의 파이프라인으로 통합한다.

---

## 카나리 배포 — Flagger

### Flagger란

Flagger는 Kubernetes에서 **자동화된 점진적 배포**를 수행하는 도구다.
Linkerd의 트래픽 메트릭을 기반으로 새 버전의 건강 상태를 평가하고,
기준을 충족하면 트래픽을 점진적으로 이동시킨다.

### 카나리 배포 흐름

```
1. 새 버전 배포 (이미지 태그 변경)
        │
2. Flagger가 감지 → 카나리 Pod 생성
        │
3. 트래픽 5% → 카나리로 라우팅
        │
4. Linkerd 메트릭 평가 (성공률 > 99%, p99 < 500ms)
        │
   ┌────┴────┐
   │ 통과?    │
   ├── Yes ──→ 트래픽 10% → 30% → 60% → 100%
   │          → Primary 교체 완료
   │
   └── No ───→ 자동 롤백
              → 카나리 Pod 제거
              → Slack/알림 발송
```

### Flagger Canary CRD 예시

```yaml
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: carry-order
  namespace: carry
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: carry-order
  service:
    port: 8080
  analysis:
    # 평가 주기 및 횟수
    interval: 30s
    threshold: 5          # 5회 연속 실패 시 롤백
    maxWeight: 60         # 최대 카나리 트래픽 비율
    stepWeight: 10        # 단계별 증가량 (10% 씩)

    # Linkerd 메트릭 기반 평가 기준
    metrics:
      - name: request-success-rate
        thresholdRange:
          min: 99           # 성공률 99% 이상
        interval: 1m
      - name: request-duration
        thresholdRange:
          max: 500          # p99 레이턴시 500ms 이하
        interval: 1m

    # 카나리 배포 시 자동 웹훅 (선택)
    webhooks:
      - name: load-test
        type: rollout
        url: http://flagger-loadtester.carry/
        metadata:
          cmd: "hey -z 1m -q 10 -c 2 http://carry-order-canary.carry:8080/health"
```

### Flagger + Linkerd TrafficSplit

Flagger는 내부적으로 Linkerd의 `TrafficSplit` CRD를 생성하여 트래픽을 분할한다:

```yaml
apiVersion: split.smi-spec.io/v1alpha2
kind: TrafficSplit
metadata:
  name: carry-order
  namespace: carry
spec:
  service: carry-order
  backends:
    - service: carry-order-primary
      weight: 900       # 90%
    - service: carry-order-canary
      weight: 100       # 10%
```

### 카나리 배포 시나리오

```
개발자: git push → main 머지
    │
GitHub Actions:
    ├── 빌드 & 테스트
    ├── Docker 이미지 빌드 & 푸시
    └── Kubernetes Deployment 이미지 태그 업데이트
           │
Flagger: 새 이미지 감지
    ├── 카나리 Pod 생성
    ├── 트래픽 0% → 10% → 20% → ... → 60%
    │   (각 단계에서 Linkerd 메트릭 평가)
    │
    ├── 성공 → Primary 교체, 카나리 제거
    └── 실패 → 롤백, 알림 발송
```

---

## 로컬 개발 vs 프로덕션 구성

| 컴포넌트 | 로컬 (Docker Compose) | 프로덕션 (Kubernetes) |
|----------|----------------------|---------------------|
| OTel Collector | 컨테이너 | DaemonSet / Sidecar |
| Jaeger | All-in-one 컨테이너 | Jaeger Operator |
| Prometheus | (OTel에서 직접 export) | Linkerd Viz + OTel |
| Linkerd | 미사용 | Control Plane + Proxy |
| Flagger | 미사용 | Flagger Controller |
| 트래픽 분할 | 미사용 | TrafficSplit CRD |

로컬에서는 OTel + Jaeger만으로 트레이싱을 검증하고,
Kubernetes 환경에서 Linkerd + Flagger를 추가하여 서비스 메시와 카나리 배포를 실증한다.

---

## 관측 가능성 체크리스트

- [ ] OTel SDK 자동 계측 (Spring Boot, JPA, Kafka)
- [ ] Kafka 메시지에 W3C TraceContext 전파
- [ ] Outbox 테이블에 traceId 저장
- [ ] Jaeger에서 Saga 전체 흐름 트레이스 확인
- [ ] 커스텀 비즈니스 메트릭 (주문 수, Saga 소요 시간)
- [ ] 구조화 로깅 (JSON 포맷, traceId 포함)
- [ ] Linkerd 설치 및 mTLS 활성화 (K8s 환경)
- [ ] Flagger 카나리 배포 시나리오 실행
- [ ] Linkerd 메트릭 → Flagger 평가 기준 연동
