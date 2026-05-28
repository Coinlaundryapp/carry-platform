# 10. 프로덕션 준비도 평가 및 개선 로드맵

> 최종 수정일: 2026-03-13
> 상태: Active
> 평가 기준일: Phase 3 Observability 통합 완료 시점

---

## 평가 요약

| 영역 | 점수 | 판정 |
|------|------|------|
| 아키텍처 설계 | 80/100 | 실무 수준 |
| 코드 품질 | 60/100 | 보완 필요 |
| 운영 준비도 | 30/100 | 프로덕션 투입 불가 |

### 강점

```
[✓] Modular Monolith + Hexagonal Architecture — 모듈 간 의존성 완전 분리
[✓] Transactional Outbox + Debezium CDC — 이벤트 유실 방지 정석 패턴
[✓] Choreography Saga + 멱등성 보장 — ProcessedEvent 기반 중복 처리 방지
[✓] Observability 3대 기둥 — Trace 전파, JSON 로깅, 비즈니스 메트릭
[✓] 설계 문서 01~09 — 의사결정 근거와 아키텍처가 체계적으로 기록됨
[✓] 커서 기반 페이지네이션, @AuthenticationPrincipal 등 API 설계 표준화
```

### 약점 분류

```
                 영향도 높음
                    │
    ┌───────────────┼───────────────┐
    │               │               │
    │   P0: 치명적   │   P1: 중요    │
    │   (4건)       │   (5건)       │
    │               │               │
────┼───────────────┼───────────────┼──── 긴급도
    │               │               │
    │   P2: 보완    │   P3: 개선    │
    │   (3건)       │   (4건)       │
    │               │               │
    └───────────────┼───────────────┘
                    │
                 영향도 낮음
```

---

## P0: 치명적 — 프로덕션 투입 차단

이 항목들이 해결되지 않으면 프로덕션 배포 자체가 불가능하다.

### P0-1. 통합 테스트 부재

**현황**: Testcontainers 의존성은 추가되어 있지만, 실제 DB/Kafka 연동 통합 테스트가 없음. Saga 흐름이 실제로 동작하는지 코드 수준에서 검증 불가.

**위험**: 단위 테스트는 mock 기반이라 JPA 쿼리 오류, Kafka 직렬화 문제, 트랜잭션 경계 오류 등을 잡지 못함.

**해결 방안**:
```
1. IntegrationTestBase 추상 클래스 작성
   - Testcontainers로 PostgreSQL + Kafka 컨테이너 공유
   - @DynamicPropertySource로 테스트 프로퍼티 주입

2. 모듈별 통합 테스트 작성 (우선순위순)
   a. Outbox 통합: 주문 생성 → outbox_events 저장 검증
   b. Consumer 통합: Kafka 메시지 발행 → Consumer 처리 → DB 상태 검증
   c. Repository 통합: 커서 페이지네이션, 복합 조건 쿼리 검증

3. Saga E2E 테스트
   - 주문 → 배차 → 수거 → 결제 → 완료 정상 흐름
   - 결제 실패 → 주문 취소 보상 흐름
   - 배차 타임아웃 → 주문 취소 보상 흐름
```

**작업량**: 약 15~20개 테스트 클래스

---

### P0-2. 보상 트랜잭션 / 장애 복구 불완전

**현황**: Saga 이벤트 소비 실패 시 재시도 전략과 Dead Letter Queue(DLQ)가 없음. Consumer에서 예외 발생 시 Spring Kafka 기본 동작(무한 재시도 또는 메시지 유실)에 의존.

**위험**: 네트워크 일시 장애나 DB 일시 불능 시 이벤트가 유실되거나 무한 재시도로 Consumer가 블로킹됨.

**해결 방안**:
```
1. Kafka Consumer 에러 핸들링 설정
   ┌──────────────┐     3회 재시도     ┌──────────┐     실패     ┌──────────┐
   │   Consumer   │ ──────────────→  │  Retry   │ ─────────→  │   DLQ    │
   │   (원본 토픽) │   (1s, 3s, 9s)   │  Topic   │             │  Topic   │
   └──────────────┘                   └──────────┘             └──────────┘
                                                                    │
                                                              수동/자동 재처리
                                                              + 알림 발송

2. 구현 상세
   - DefaultErrorHandler + FixedBackOff(1000ms, 3회)
   - DeadLetterPublishingRecoverer → *.DLQ 토픽으로 전송
   - DLQ 메시지에 원본 토픽, 파티션, 오프셋, 예외 메시지 헤더 포함

3. DLQ 모니터링
   - outbox.dlq.count 메트릭 등록 (BusinessMetrics)
   - DLQ 메시지 발생 시 로그 레벨 ERROR + 알림
```

---

### P0-3. Branch Protection 미설정

**현황**: GitHub 레포에 branch protection rule이 없어서 CI 실패 상태에서도 main/develop에 직접 머지 가능.

**위험**: 빌드 깨진 코드가 main에 들어갈 수 있음. 실무에서는 사고 수준.

**해결 방안**:
```
main 브랜치:
  ✓ Require pull request before merging
  ✓ Require status checks: build-and-test
  ✓ Require branches to be up to date
  ✓ Restrict direct pushes

develop 브랜치:
  ✓ Require status checks: build-and-test
  ✓ Require pull request before merging
```

**작업량**: GitHub Settings에서 5분 내 설정 가능

---

### P0-4. 시크릿 관리 체계 부재

**현황**: JWT secret이 `application-local.yml`에 하드코딩. dev/prod는 환경변수 참조(`${JWT_SECRET}`)이지만, 실제 주입 검증이 안 됨. 환경변수 미설정 시 앱이 기동되면서 빈 시크릿으로 동작할 가능성.

**위험**: JWT secret 유출 시 전체 인증 체계 무력화. 환경변수 미설정 시 보안 구멍.

**해결 방안**:
```
1. 시크릿 검증 강제
   - ApplicationRunner에서 JWT secret 길이/존재 검증
   - 미설정 시 앱 기동 실패 (fail-fast)

2. 시크릿 관리 도구 도입 (Phase 5 이전)
   - 로컬/dev: .env 파일 (gitignore 등록)
   - prod: Kubernetes Secrets 또는 AWS Secrets Manager

3. .env.example 파일 제공
   - 필요한 환경변수 목록과 설명 문서화
```

---

## P1: 중요 — 운영 시 즉시 문제 발생

프로덕션 배포는 가능하나, 운영 중 장애 대응이 어려운 항목들.

### P1-1. Circuit Breaker 부재

**현황**: `PaymentCommandService`에서 PG사 API 호출 시 타임아웃/장애 보호 없음. PG사 장애 시 스레드 블로킹으로 전체 결제 기능 마비.

**해결 방안**:
```kotlin
// Resilience4j Circuit Breaker 적용
@CircuitBreaker(name = "pg-gateway", fallbackMethod = "onPgFailure")
fun requestPayment(request: PgPaymentRequest): PgPaymentResult {
    return gateway.requestPayment(request)
}

// 설정
resilience4j.circuitbreaker.instances.pg-gateway:
  sliding-window-size: 10
  failure-rate-threshold: 50
  wait-duration-in-open-state: 30s
  permitted-number-of-calls-in-half-open-state: 3
```

**적용 대상**:
| 외부 연동 | 모듈 | 우선순위 |
|-----------|------|---------|
| PG사 결제/환불 | carry-payment | 높음 |
| Kakao/Naver 지오코딩 | carry-geo | 중간 |
| S3 파일 업로드 | carry-media | 낮음 |

---

### P1-2. Dispatch Timeout 스케줄러 미구현

**현황**: `DispatchTimeoutEvent`가 이벤트/메트릭에 정의되어 있지만, 실제 타임아웃 감지 및 이벤트 발행 로직이 없음.

**위험**: 배차 요청 후 캐리어가 수락하지 않으면 주문이 영원히 대기 상태.

**해결 방안**:
```
1. DispatchTimeoutScheduler 구현
   - @Scheduled(fixedRate = 60_000)
   - PENDING 상태 + 생성 후 N분 경과 배차 조회
   - DispatchTimeoutEvent 발행 → Order 모듈에서 취소 처리

2. 타임아웃 설정 외부화
   - dispatch.timeout-minutes: 15 (application.yml)
```

---

### P1-3. Flyway 마이그레이션 미검증

**현황**: local 프로파일에서 Flyway disabled + JPA ddl-auto=update. 실제 마이그레이션 파일(`db/migration/`)이 dev/prod 환경에서 정상 동작하는지 검증되지 않음.

**위험**: 스키마 변경 시 데이터 손실, 롤백 불가, 환경 간 스키마 불일치.

**해결 방안**:
```
1. local에서도 Flyway 활성화 (ddl-auto=validate)
   - 개발 중 스키마 변경을 마이그레이션 파일로 관리하는 습관 강제

2. CI에서 Flyway 검증 추가
   - Testcontainers PostgreSQL에 Flyway 마이그레이션 적용 후 validate

3. 마이그레이션 파일 누락 체크
   - 엔티티 변경 PR에 마이그레이션 파일 포함 여부 리뷰 체크리스트화
```

---

### P1-4. API Rate Limiting 부재

**현황**: 모든 API 엔드포인트에 요청 제한이 없음.

**위험**: DDoS, 무차별 대입 공격, 비정상 클라이언트로 인한 서버 과부하.

**해결 방안**:
```
1. 단기 (모놀리스 단계)
   - Spring MVC 인터셉터 + Redis 기반 Sliding Window Rate Limiter
   - 엔드포인트별 차등 제한:
     POST /api/v2/orders    → 10 req/min per user
     GET  /api/v2/orders    → 60 req/min per user
     POST /api/v2/payments  → 5  req/min per user

2. 장기 (Phase 5, API Gateway)
   - Kong/Spring Cloud Gateway의 Rate Limiting 플러그인
```

---

### P1-5. Graceful Shutdown 미처리

**현황**: 앱 종료 시 Kafka Consumer의 현재 처리 중인 메시지 오프셋 커밋이 보장되지 않음.

**위험**: 배포 시 이벤트 재처리 또는 유실. 멱등성 처리(ProcessedEvent)가 있어 중복은 방지되지만, 처리 중이던 트랜잭션이 롤백되면 이벤트를 다시 받지 못할 수 있음.

**해결 방안**:
```yaml
# application.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
server:
  shutdown: graceful

# Kafka Consumer
spring.kafka.listener:
  ack-mode: MANUAL_IMMEDIATE  # 또는 RECORD
```
```kotlin
// KafkaConfig에 ContainerCustomizer 추가
@Bean
fun kafkaListenerContainerFactoryCustomizer(): ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>> {
    return ContainerCustomizer { container ->
        container.containerProperties.isStopContainerWhenFenced = true
    }
}
```

---

## P2: 보완 — 안정성 향상

### P2-1. 에러 로깅 구조화

**현황**: `GlobalExceptionHandler`에서 예외 로깅은 하지만 구조화된 컨텍스트(orderId, userId 등)가 부족.

**해결 방안**:
- MDC에 요청별 userId, 주요 파라미터 자동 삽입하는 필터 추가
- 이미 logback-spring.xml에 MDC 키(userId, orderId) 설정은 되어 있으므로, 실제 MDC.put 호출만 추가하면 됨

---

### P2-2. 헬스체크 세분화

**현황**: Spring Boot Actuator `/actuator/health` 기본 설정만 사용.

**해결 방안**:
```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
      group:
        readiness:
          include: db, kafka, redis
        liveness:
          include: ping
  health:
    kafka:
      enabled: true
    redis:
      enabled: true
```

---

### P2-3. 테스트 커버리지 측정

**현황**: 테스트가 존재하지만 커버리지 측정 도구(JaCoCo)가 설정되지 않아 실제 커버리지를 알 수 없음.

**해결 방안**:
- JaCoCo Gradle 플러그인 추가
- CI에서 커버리지 리포트 생성 + PR 코멘트
- 최소 커버리지 threshold 설정 (예: line 60%, branch 50%)

---

## P3: 개선 — 차별화 요소

### P3-1. E2E Saga 테스트

주문 생성부터 배달 완료까지 전체 흐름을 Testcontainers 환경에서 검증.
(상세는 `08-testing.md` E2E 섹션 참고)

### P3-2. 부하 테스트

k6 또는 Gatling으로 주요 API 성능 베이스라인 측정.
- 목표: 주문 생성 API p99 < 500ms, 동시 100 요청 처리

### P3-3. Prometheus Alerting Rules

비즈니스 메트릭 기반 알람:
```yaml
# 예시
- alert: HighPaymentFailureRate
  expr: rate(payment_failed_count_total[5m]) / rate(payment_completed_count_total[5m]) > 0.1
  for: 5m
  annotations:
    summary: "결제 실패율 10% 초과"
```

### P3-4. API 버저닝 전략 고도화

현재 URL 프리픽스(`/api/v2`)만 사용. 향후 Breaking Change 시 헤더 기반 버저닝 또는 Content Negotiation 고려.

---

## 개선 로드맵

```
                    2026-03
       W3              W4
  ─────┬───────────────┬──────────────────
       │               │
       │  Sprint 1     │  Sprint 2
       │               │
       │  P0-3 Branch  │  P0-1 통합 테스트
       │  Protection   │  (모듈별)
       │  (30분)       │
       │               │  P0-2 DLQ/재시도
       │  P0-4 시크릿   │  설정
       │  검증 + .env   │
       │  (2시간)      │  P1-2 Dispatch
       │               │  Timeout 스케줄러
       │  P1-3 Flyway  │
       │  local 활성화  │  P1-5 Graceful
       │  (1시간)      │  Shutdown
       │               │

                    2026-04
       W1              W2
  ─────┬───────────────┬──────────────────
       │               │
       │  Sprint 3     │  Sprint 4
       │               │
       │  P0-1 Saga    │  P1-4 Rate
       │  E2E 테스트    │  Limiting
       │               │
       │  P1-1 Circuit │  P2-1 MDC 필터
       │  Breaker      │
       │  (Payment)    │  P2-2 헬스체크
       │               │  세분화
       │  P2-3 JaCoCo  │
       │  설정         │  P3-3 Alerting
       │               │  Rules
```

---

## 우선순위별 체크리스트

### Sprint 1 (즉시, 1~2일)
- [ ] GitHub branch protection 설정 (main, develop)
- [ ] JWT secret fail-fast 검증 로직 추가
- [ ] `.env.example` 파일 작성
- [ ] Flyway local 프로파일 활성화 + ddl-auto=validate 전환
- [ ] 기존 스키마를 Flyway baseline 마이그레이션으로 확정

### Sprint 2 (1주)
- [ ] IntegrationTestBase 작성 (Testcontainers PostgreSQL + Kafka)
- [ ] Outbox 저장 통합 테스트 (Order, Payment, Dispatch)
- [ ] Kafka Consumer 통합 테스트 (이벤트 수신 → DB 상태 변경)
- [ ] Kafka DLQ + 재시도 설정 (DefaultErrorHandler + DeadLetterPublishingRecoverer)
- [ ] DispatchTimeoutScheduler 구현
- [ ] Graceful Shutdown 설정

### Sprint 3 (1주)
- [ ] Saga E2E 테스트 (정상 흐름 + 보상 트랜잭션)
- [ ] Resilience4j Circuit Breaker 적용 (PaymentGateway)
- [ ] JaCoCo 설정 + CI 리포트

### Sprint 4 (1주)
- [ ] Redis Rate Limiter 구현
- [ ] MDC userId/orderId 자동 삽입 필터
- [ ] 헬스체크 readiness/liveness 그룹 분리
- [ ] Prometheus alerting rules 초안

---

## 완료 기준

모든 P0 해소 + P1 80% 해소 시 **프로덕션 투입 최소 조건 충족**으로 판정:

```
프로덕션 투입 최소 조건:
  [P0] 통합 테스트 커버: Saga 정상/보상 흐름 각 1건 이상
  [P0] DLQ + 재시도: 모든 Consumer에 적용
  [P0] Branch Protection: main, develop 설정 완료
  [P0] 시크릿: fail-fast 검증 동작 확인
  [P1] Circuit Breaker: PG 연동에 적용
  [P1] Dispatch Timeout: 스케줄러 동작 확인
  [P1] Graceful Shutdown: 배포 시 이벤트 유실 없음 확인

검증 방법:
  ./gradlew build                       → 전체 테스트 통과
  docker compose up + 수동 Saga 흐름     → Jaeger 트레이스 확인
  PG 장애 시뮬레이션                      → Circuit Breaker 동작 확인
  앱 재시작 중 이벤트 발행                 → DLQ/재처리 동작 확인
```

---

> 본 문서는 개선 진행에 따라 체크리스트를 업데이트한다.
> 각 Sprint 완료 시 해당 항목에 완료 날짜를 기록한다.
