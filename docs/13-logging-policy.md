# 13. 로깅 정책 — 상관관계와 레벨 분류

> 최종 수정일: 2026-05-29
> 상태: Phase 3.3 — Active

ROADMAP Phase 3.3 산출물. 사가 라이프사이클을 한 줄씩 따라갈 수 있게 만들고, 로그 레벨이 운영 알럿 신호와 일치하도록 정리한다.

---

## MDC 키

| 키 | 채우는 주체 | 의미 |
|---|---|---|
| `traceId`, `spanId` | Micrometer Tracing 자동 | OpenTelemetry 트레이스 식별자. HTTP 요청·Kafka consume 범위. |
| `saga.traceId` | `EventConsumerSupport.processIfNotDuplicate` | 사가 시작 시 발급되어 이벤트 페이로드에 포함되는 식별자. 동일 사가의 모든 이벤트 처리에 같은 값. |
| `orderId` | `SagaLogContext.withOrderId` (handler 진입) | 처리 중인 사가의 핵심 도메인 키. order, dispatch, delivery, payment 등 어느 모듈에서 로그를 찍든 같은 주문이면 같은 값. |
| `userId` | (예약) | 인증 필터에서 채울 예정 — Phase 4 보안 작업. |

### 로컬 콘솔 패턴

```
HH:mm:ss.SSS [thread] LEVEL logger [trace=...,span=...,saga=...,order=...] - 메시지
```

### 비로컬(JSON) 인코더

`logback-spring.xml`의 `<includeMdcKeyName>` 목록에 위 키가 모두 등록돼 있어 자동으로 JSON 필드로 노출된다.

---

## Saga 핸들러 작성 규칙

```kotlin
@Service
class XxxSagaHandler(...) : XxxEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onSomeEvent(event: SomeEvent) {
        SagaLogContext.withOrderId(event.orderId) {
            log.info("Xxx saga: onSomeEvent <식별 가능한 핵심 필드들>")
            // ... 비즈니스 본문
        }
    }
}
```

원칙:
- **핸들러 진입 시 1회 `log.info`** — orderId 외의 핵심 식별자(dispatchId, paymentId 등)는 로그 메시지에. 그 안에서 `log.info`가 더 필요하면 자유롭게.
- **MDC 정리는 `withOrderId`가 try/finally로 보장.** 직접 `MDC.put/remove` 호출 금지(누수 위험).
- **return/throw도 안전**: 중간 `return@withOrderId`로 빠져나가도 finally가 동작한다.

---

## 로그 레벨 분류 정책

`GlobalExceptionHandler` 기준:

| 상황 | 레벨 | 근거 |
|---|---|---|
| `BusinessException` status ≥ 500 (예: `PG_GATEWAY_UNAVAILABLE 503`) | `error` | 인프라 장애. 알럿 대상. |
| `BusinessException` status 4xx (예: `ORDER_NOT_FOUND 404`, `INVOICE_ALREADY_PAID 409`) | `info` | 정상적인 비즈니스 조건. 트래픽 분석용. 알럿 X. |
| `MethodArgumentNotValidException` 등 검증 4xx | `warn` | 클라이언트가 잘못한 것. 빈도 추적 가치 있음. |
| `OptimisticLockingFailureException` (409) | `warn` | 동시 수정 충돌. 빈도가 높아지면 핫스팟 신호. |
| 기타 `Exception` (500) | `error` | 처리하지 않은 예외. 알럿 대상. |

> **운영 알럿은 `error` 레벨만 보면 된다**는 규칙을 유지하는 것이 목적. `BusinessException` 4xx를 `warn`/`error`로 두면 정상 동작이 알럿을 만든다.

### 도메인 서비스·핸들러에서 직접 잡는 경우

- 예상되는 비즈니스 조건 분기는 `info` 또는 `warn`.
- 외부 의존성 호출 실패(DB, PG, Kafka)는 `error`로 명시.
- `try { ... } catch (e: Exception) { log.error(...); throw }` 패턴은 메시지가 충분히 자세할 때만 의미가 있다. 단순 rethrow면 GlobalExceptionHandler에 위임.

---

## 다음 단계

- **Phase 3.4 알럿 기준선** — `error` 레벨 로그의 5분 증가량을 Prometheus alert로 잡는 등의 룰. `docs/11-business-metrics.md` 표를 alert rules YAML로 구체화.
- **Phase 3.5 운영 런북** — 각 알럿이 어느 로그 패턴과 매칭되는지 매핑.
- **`userId` MDC 활성화** — Phase 4 보안 작업에서 인증 필터가 `MDC.put("userId", ...)` 수행.
