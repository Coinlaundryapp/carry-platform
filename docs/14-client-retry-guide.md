# 14. 클라이언트 재시도 가이드

> 최종 수정일: 2026-07-12
> 상태: Active

클라이언트(앱·웹·외부 연동)가 carry-platform API의 에러 응답을 받았을 때 **재시도해도 되는지, 어떻게 재시도해야 하는지**를 정의한다. 서버는 명령측 멱등성(`Idempotency-Key`)과 낙관적 락(`@Version`)으로 안전한 재시도를 보장하도록 설계돼 있다 — 이 가이드는 그 클라이언트 측 사용법이다.

---

## 에러 응답 봉투

모든 에러는 공통 `ApiResponse` 봉투로 반환된다 (`carry-common` `GlobalExceptionHandler`):

```json
{
  "status": 409,
  "code": "CONCURRENT_MODIFICATION",
  "message": "Concurrent modification detected — retry the request",
  "traceId": "f1c0..."
}
```

- `code` = `ErrorCode` enum 이름. **재시도 판단은 HTTP 상태가 아니라 `code`로 한다** (같은 409라도 재시도 가능 여부가 다르다).
- `traceId`는 문의/디버깅 시 서버 로그·Jaeger와 상관관계를 잇는 키다. 에러 리포트에 항상 포함하라.

---

## 재시도 판단표

### ✅ 재시도 가능 (transient)

| code | status | 의미 | 재시도 방법 |
|---|---|---|---|
| `CONCURRENT_MODIFICATION` | 409 | 낙관적 락 충돌 — 다른 요청이 같은 리소스를 먼저 수정 | **최신 상태를 다시 조회한 후** 같은 의도가 여전히 유효하면 재시도. 즉시 1~2회면 대부분 해소 |
| `IDEMPOTENT_REQUEST_IN_PROGRESS` | 409 | 같은 `Idempotency-Key` 요청이 처리 중 | 짧게 대기 후 **같은 키로** 재요청 — 처리가 끝나 있으면 저장된 결과가 재생된다 |
| `PG_GATEWAY_UNAVAILABLE` | 503 | 결제 게이트웨이 Circuit Breaker OPEN | 지수 백오프로 재시도 (아래 백오프 정책) |
| `GEOCODING_UNAVAILABLE` | 503 | 지오코딩 Circuit Breaker OPEN (캐시 미스) | 지수 백오프로 재시도 |
| `OAUTH_PROVIDER_UNAVAILABLE` | 503 | OAuth 공급자(Kakao) 장애 | 지수 백오프로 재시도 |
| `GEOCODING_FAILED` / `REVERSE_GEOCODING_FAILED` | 502 | 업스트림 일시 실패 | 1~2회 제한적 재시도, 계속 실패하면 중단 |
| `PAYMENT_FAILED` | 502 | PG 처리 실패(빌링키 등록 등 동기 호출 경로에서만 클라이언트에 노출 — 자동과금 실패는 서버 내부에서 `ChargeRetrySweeper`가 처리하며 클라이언트에 이 코드로 노출되지 않는다) | 짧게 대기 후 동일 요청 재시도, 반복되면 카드 재등록 흐름 재시작 |
| `MEDIA_UPLOAD_FAILED` | 502 | 업로드 일시 실패 | 1~2회 제한적 재시도 |
| (코드 없음) | 500 `INTERNAL_ERROR` | 미처리 예외 | 1회 재시도 가능하나, 반복되면 `traceId`와 함께 리포트 |

### ❌ 재시도 금지 (영구 조건)

- **400/401/403/404/422 전부** — 입력 오류, 인증/인가 실패, 부재, 비즈니스 규칙 위반. 같은 요청을 다시 보내도 결과가 같다. 요청을 수정하거나 사용자에게 안내한다.
- **409 중 상태 충돌류** — `ORDER_NOT_CANCELLABLE`, `PAYMENT_ALREADY_COMPLETED`, `DISPATCH_ALREADY_ACCEPTED`, `DUPLICATE_REVIEW`, `INVOICE_ALREADY_PAID` 등은 "이미 그렇게 됐다"는 뜻이다. 재시도가 아니라 **최신 상태를 조회해 UI를 동기화**하라.
- `409 BILLING_KEY_REQUIRED` — 주문 생성 전제조건 미충족(활성 빌링키 없음). 카드 등록 화면(`POST /api/v2/billing-keys`)으로 유도한 뒤, 등록 성공 후 **같은 주문 생성 요청을 재시도**하라(같은 Idempotency-Key 재사용 가능 — 이전 시도는 실패로 끝나 예약 슬롯이 남지 않는다).
- `409 OVERDUE_INVOICE_EXISTS` — 연체(72h 초과 미결제) 인보이스가 있어 신규 주문이 차단됨. 고객에게 미결제 안내 후, 카드 재등록을 유도하면 다음 자동과금 스윕에서 연체가 해소되고 차단이 자연 해제된다. 즉시 재시도는 의미 없다 — 스윕 주기(수 시간) 이후 재시도하거나 결제 완료 알림을 받은 뒤 재시도.

---

## Idempotency-Key 사용법 (생성 명령)

이중 생성이 치명적인 생성 명령 2종은 `Idempotency-Key` 헤더(선택)를 지원한다:

| 엔드포인트 | 방지하는 사고 |
|---|---|
| `POST /api/v2/orders` | 주문 중복 생성 |
| `POST /api/v2/reviews` | 리뷰 중복 작성 |

> 결제(자동과금)는 더 이상 클라이언트가 호출하는 명령이 아니다 — `InvoiceIssuedEvent`→`AutoChargeService`가 서버 내부에서 트리거하며, PG 멱등키는 `charge-{invoiceId}`로 고정되어 서버가 자체 관리한다. 클라이언트가 다루는 결제 관련 명령은 빌링키 등록(`POST /api/v2/billing-keys`, Idempotency-Key 미지원 — 재시도 시 기존 활성 키를 교체하는 멱등 동작 자체가 안전하다)뿐이다.

규칙:

1. **논리적 시도 1건당 키 1개** — 클라이언트가 UUID를 생성해 헤더로 보낸다. 네트워크 타임아웃·연결 끊김 후 "그 요청이 들어갔는지 모를 때" **같은 키로 다시 보내면** 서버가 저장된 결과를 재생한다(중복 생성 없음).
2. **사용자가 의도한 새로운 시도는 새 키** — 예: `BILLING_KEY_REQUIRED`(409)를 받고 사용자가 카드를 등록한 뒤 "다시 주문"을 누르면, 실패로 끝난 이전 시도는 예약 슬롯을 남기지 않으므로 **같은 키를 재사용해도 안전**하다. 반대로 완전히 다른 새 주문 의도라면 새 UUID를 발급한다.
3. **동시 요청 레이스의 패자는 같은 키로 최대 120초간 `IDEMPOTENT_REQUEST_IN_PROGRESS`(409)를 받을 수 있다** — 진행 마커(pendingTtl)가 만료될 때까지다. 짧게 대기 후 같은 키로 재요청하면 저장된 결과가 재생된다.

---

## 백오프 정책 (권장)

```
시도 n의 대기시간 = min(base × 2^(n-1), cap) + jitter
base = 500ms, cap = 8s, jitter = 0~300ms 균등 난수, 최대 4회
```

- 503(Circuit Breaker OPEN)은 서버가 회복 창을 갖도록 **cap을 크게**(8초) 둔다. CB는 보통 수초~수십초 내 half-open으로 전이한다.
- `CONCURRENT_MODIFICATION`은 백오프 없이 **재조회 후 즉시 1~2회**가 적절하다 — 충돌 상대는 이미 커밋을 끝냈다.
- 모바일 클라이언트는 백그라운드 진입 시 재시도 루프를 중단하고, 복귀 시 멱등 키 재전송으로 결과를 확인하는 편이 낫다.

---

## 참고

- 에러 코드 전체 목록: `carry-common` `ErrorCode.kt`
- 명령측 멱등성 설계: PR #101(주문)·#120(결제·리뷰 확장, `IdempotencyStore` 공유 추출)
- 낙관적 락 적용 범위: Order + Payment·Delivery·Review(PR #118)
- 서버측 로그 레벨 정책(왜 4xx는 알럿이 안 울리는가): [13-logging-policy.md](13-logging-policy.md)
