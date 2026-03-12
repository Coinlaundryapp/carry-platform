# 09. 마이그레이션 로드맵, 마이크로서비스 분리, 배포 전략

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 로드맵 개요

```
Phase 1          Phase 2          Phase 3          Phase 4          Phase 5          Phase 6
프로젝트 기반     핵심 도메인       주문 Saga        부가 도메인       마이크로서비스    서비스 메시
                                                  + 운영           분리             + 카나리 배포
───────────── → ─────────────── → ────────────── → ────────────── → ────────────── → ──────────────
Gradle 멀티모듈   User             Order            Operation        K8s 전환         Linkerd
Docker Compose   Laundromat       Payment          Review           서비스 분리       Flagger
CI/CD            Price            Dispatch         Notification     DB 분리          카나리 배포
공통/인프라 모듈   Geo              CDC/Debezium     Media            API Gateway      Chaos Test
                                  Saga 통합 테스트   Service Avail.
                                  Observability
```

---

## Phase 1: 프로젝트 기반 구축

### 목표
Kotlin + Spring Boot + JPA 멀티 모듈 프로젝트의 골격을 완성한다.

### 작업 목록

- [ ] Gradle 멀티 모듈 프로젝트 초기화
  - Kotlin 2.x, Java 21, Spring Boot 3.4.x
  - `settings.gradle.kts`에 전체 모듈 선언
  - 루트 `build.gradle.kts`에 공통 플러그인/의존성
- [ ] 공통 모듈 셋업
  - `carry-common`: 예외, API 응답, 유틸리티
  - `carry-event`: 이벤트 계약 (순수 Kotlin data class)
  - `carry-security`: JWT, SecurityConfig
- [ ] 인프라 모듈 셋업
  - `carry-infra-persistence`: JPA BaseEntity, Auditing, QueryDSL
  - `carry-infra-kafka`: Kafka 공통 설정, Outbox 공통 코드
  - `carry-infra-redis`: Redis 설정
  - `carry-infra-s3`: S3 클라이언트
  - `carry-infra-observability`: OTel 설정
- [ ] `carry-app` 모듈
  - `@SpringBootApplication`
  - `application.yml` (Virtual Threads 설정 포함)
  - Profile 분리 (local, dev, prod)
- [ ] Docker Compose
  - PostgreSQL, Kafka (KRaft), Kafka Connect + Debezium
  - Redis, OTel Collector, Jaeger
- [ ] CI/CD 파이프라인 (GitHub Actions)
  - 빌드 → 테스트 → Docker 이미지 빌드/푸시
- [ ] ArchUnit 기본 규칙 작성
  - 모듈 경계, 계층 의존성 규칙

### 완료 기준
- `./gradlew build` 성공
- `docker compose up`으로 전체 인프라 기동
- ArchUnit 테스트 통과
- GitHub Actions CI 통과

---

## Phase 2: 핵심 도메인 모듈

### 목표
Saga에 직접 관여하지 않는 기반 도메인을 먼저 구현한다.

### 작업 목록

- [ ] `carry-user` — 인증, 회원, 배송지
  - Kakao OAuth + JWT 마이그레이션
  - User, ShippingAddress 엔티티 (JPA)
  - 약관 동의 로직
- [ ] `carry-laundromat` — 세탁소
  - PostGIS 위치 기반 검색
  - Laundromat 엔티티 + 옵션 매핑
- [ ] `carry-price` — 가격 정책
  - Strategy Pattern 가격 계산
  - DB에서 가격 정책 로딩
- [ ] `carry-geo` — 위치 서비스
  - Naver/Kakao 지오코딩 API 연동

### 완료 기준
- 각 모듈의 REST API가 동작
- 단위 테스트 + 통합 테스트 작성
- Swagger에서 API 확인 가능

---

## Phase 3: 주문 Saga 구현

### 목표
주문 흐름의 핵심인 Order → Payment → Dispatch Saga를 완성한다.

### 작업 목록

- [ ] `carry-order` — 주문 도메인
  - Order Aggregate Root (JPA)
  - 주문 상태 머신
  - Outbox 이벤트 발행 (`OrderCreatedEvent`, `OrderPaidEvent`)
  - PaymentCompletedEvent 소비자
- [ ] `carry-payment` — 결제 도메인
  - Payment 엔티티
  - OrderCreatedEvent 소비자 → 결제 레코드 생성
  - PG 연동 (또는 Mock)
  - PaymentCompletedEvent / PaymentFailedEvent 발행
- [ ] `carry-dispatch` — 배차 도메인
  - Dispatch Aggregate Root
  - OrderPaidEvent 소비자 → 배차 생성
  - DispatchCreatedEvent 발행
- [ ] Debezium Connector 설정
  - Outbox 테이블 감시 → Kafka 토픽 발행
  - Outbox Event Router SMT 설정
- [ ] Saga 통합 테스트
  - 정상 흐름 E2E 테스트
  - 보상 트랜잭션 테스트 (결제 실패 → 주문 취소)
- [ ] Observability 연동
  - OTel 자동 계측 확인
  - Jaeger에서 Saga 트레이스 시각화

### 완료 기준
- 주문 생성 → 결제 → 배차 흐름이 이벤트로 연결
- 보상 트랜잭션 동작 확인
- Jaeger에서 전체 Saga 트레이스 조회 가능

---

## Phase 4: 부가 도메인 및 운영

### 작업 목록

- [ ] `carry-operation` — 운영 대시보드 (신규)
  - 주문/배차 현황 조회
  - 이벤트 소비를 통한 실시간 상태 동기화
- [ ] `carry-review` — 리뷰 CRUD
- [ ] `carry-notification` — 알림 발송 (Kakao, SMS)
  - 각종 Saga 이벤트 소비 → 알림 트리거
- [ ] `carry-media` — 미디어 파일 관리
- [ ] `carry-service-availability` — 서비스 가용성

---

## Phase 5: 마이크로서비스 분리

### 목표
모듈러 모놀리스를 물리적으로 분리된 마이크로서비스로 전환한다.

### 분리 전략

#### 분리 순서

```
1단계: Notification (가장 독립적, Stateless)
2단계: Media (S3 I/O 집중, 독립적)
3단계: Dispatch (별도 스케일링 필요)
4단계: Payment (보안 요건)
5단계: Order (핵심 도메인)
마지막: User, Laundromat, etc. (필요 시)
```

#### 분리 시 변경 사항 (서비스당)

| 항목 | 모놀리스 | 분리 후 |
|------|---------|---------|
| Gradle 모듈 | 멀티 모듈 내 | 독립 프로젝트 (또는 모노레포 내 별도 앱) |
| Outbound Port | 직접 빈 주입 | HTTP/gRPC 클라이언트 |
| Kafka 이벤트 | 변경 없음 | 변경 없음 |
| Outbox + Debezium | 변경 없음 | Connector가 별도 DB를 감시 |
| DB | 같은 인스턴스, 프리픽스 | 별도 PostgreSQL 인스턴스 |
| 배포 | 단일 JAR | 서비스별 Docker 이미지 |
| 트레이싱 | 프로세스 내 전파 | Kafka 헤더 전파 (이미 구현) |

#### 서비스 분리 체크리스트 (서비스당)

```
□ 1. 독립 Gradle 프로젝트 (또는 별도 carry-app-* 모듈) 생성
□ 2. 자체 @SpringBootApplication 추가
□ 3. 자체 application.yml (DB, Kafka, OTel 설정)
□ 4. Outbound Port Adapter를 HTTP Client로 교체
□ 5. DB 테이블을 별도 인스턴스/스키마로 이관
□ 6. Debezium Connector 재설정 (새 DB 연결)
□ 7. Dockerfile 작성
□ 8. Kubernetes Deployment/Service 매니페스트
□ 9. Contract Test 추가 (API 호환성)
□ 10. 기존 모놀리스에서 해당 모듈 제거
```

### API Gateway

서비스 분리 후, 클라이언트는 API Gateway를 통해 접근한다.

```
┌──────────┐     ┌─────────────┐     ┌──────────────┐
│  Client  │────→│ API Gateway │────→│ carry-order  │
│          │     │ (Kong /     │────→│ carry-payment│
│          │     │  Spring     │────→│ carry-user   │
│          │     │  Cloud GW)  │────→│ ...          │
└──────────┘     └─────────────┘     └──────────────┘
```

Gateway 역할:
- 라우팅 (`/api/v1/orders/**` → carry-order)
- 인증 (JWT 검증)
- Rate Limiting
- CORS

### 데이터베이스 분리 상세

```
Phase 5-1: 스키마 분리
  단일 PostgreSQL 인스턴스 내에서 모듈별 스키마 생성
  ├── order_schema.orders
  ├── payment_schema.payments
  └── dispatch_schema.dispatches

Phase 5-2: 인스턴스 분리
  서비스별 독립 PostgreSQL
  ├── postgres-order:5432   → order DB
  ├── postgres-payment:5433 → payment DB
  └── postgres-dispatch:5434 → dispatch DB

  Debezium Connector도 각 DB별로 분리
```

---

## Phase 6: 서비스 메시 + 카나리 배포

### 목표
Kubernetes 위에서 Linkerd 서비스 메시와 Flagger 카나리 배포를 실증한다.

### Kubernetes 환경 구성

로컬 Kubernetes: kind 또는 minikube

```
┌───────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                     │
│                                                       │
│  ┌──────────────────────────────────────┐             │
│  │         Linkerd Control Plane        │             │
│  │   identity / destination / proxy     │             │
│  └──────────────────────────────────────┘             │
│                                                       │
│  ┌──────────────────────────────────────┐             │
│  │          Flagger Controller          │             │
│  │   (Linkerd 메트릭 기반 카나리 평가)     │             │
│  └──────────────────────────────────────┘             │
│                                                       │
│  Namespace: carry                                     │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │ order   │ │ payment │ │ dispatch │ │ notif.   │ │
│  │ +proxy  │ │ +proxy  │ │ +proxy   │ │ +proxy   │ │
│  └─────────┘ └─────────┘ └──────────┘ └──────────┘ │
│                                                       │
│  Namespace: carry-infra                               │
│  ┌──────┐ ┌───────┐ ┌──────────┐ ┌──────┐ ┌──────┐ │
│  │Kafka │ │Debez. │ │PostgreSQL│ │Redis │ │Jaeger│ │
│  └──────┘ └───────┘ └──────────┘ └──────┘ └──────┘ │
│                                                       │
│  ┌──────────────┐ ┌──────────────┐                   │
│  │OTel Collector│ │ API Gateway  │                   │
│  └──────────────┘ └──────────────┘                   │
└───────────────────────────────────────────────────────┘
```

### Kubernetes 매니페스트 구조

```
k8s/
├── base/
│   ├── namespace.yaml
│   ├── order/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── canary.yaml           # Flagger Canary CRD
│   ├── payment/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── canary.yaml
│   ├── dispatch/
│   │   └── ...
│   └── infra/
│       ├── postgres.yaml
│       ├── kafka.yaml
│       ├── redis.yaml
│       ├── debezium.yaml
│       ├── otel-collector.yaml
│       └── jaeger.yaml
├── overlays/
│   ├── local/                    # kind/minikube 설정
│   └── production/               # 프로덕션 설정
└── kustomization.yaml
```

### 카나리 배포 실증 시나리오

#### 시나리오 1: 정상 배포

```
1. carry-order 서비스에 새 기능 추가
2. PR 머지 → GitHub Actions → Docker 이미지 빌드
3. Kubernetes Deployment 이미지 태그 업데이트
4. Flagger 감지 → 카나리 Pod 생성
5. 트래픽: 0% → 10% → 20% → 40% → 60% (각 30초 간격)
6. 각 단계에서 Linkerd 메트릭 평가:
   - request-success-rate > 99%
   - request-duration p99 < 500ms
7. 모든 단계 통과 → Primary 교체 완료
```

#### 시나리오 2: 자동 롤백

```
1. 버그가 있는 버전 배포
2. Flagger 감지 → 카나리 Pod 생성
3. 트래픽: 0% → 10%
4. 성공률 99% 미달 (카나리에서 500 에러 발생)
5. 3회 연속 실패 → 자동 롤백
6. 카나리 Pod 제거, 트래픽 100% Primary 유지
7. 알림 발송 (Slack/Webhook)
```

#### 시나리오 3: Chaos Engineering 중 카나리

```
1. 정상 카나리 배포 진행 중
2. Chaos Mesh로 네트워크 지연 주입 (carry-payment 3초 지연)
3. carry-order 카나리의 레이턴시 증가 감지
4. 지연이 carry-order 자체 문제인지 외부 문제인지 Jaeger 트레이스로 확인
5. Linkerd 리트라이/타임아웃 정책이 올바르게 동작하는지 검증
```

### 배포 파이프라인 전체 흐름

```
┌─────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐
│  Code   │    │   GitHub     │    │  Container   │    │Kubernetes │
│  Push   │───→│  Actions     │───→│  Registry    │───→│  Cluster  │
│         │    │              │    │  (GHCR)      │    │           │
└─────────┘    │  ┌────────┐  │    └──────────────┘    │  Flagger  │
               │  │ Build  │  │                        │  감지     │
               │  │ Test   │  │                        │     │     │
               │  │ Image  │  │                        │     ▼     │
               │  │ Push   │  │                        │  카나리   │
               │  └────────┘  │                        │  배포     │
               └──────────────┘                        │     │     │
                                                       │  ┌──▼──┐ │
                                                       │  │평가 │ │
                                                       │  │     │ │
                                                       │  │Pass?│ │
                                                       │  └──┬──┘ │
                                                       │  Y/ \N   │
                                                       │  /   \   │
                                                       │ 승격  롤백│
                                                       └───────────┘
```

---

## CI/CD 상세

### GitHub Actions 워크플로우

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build & Test
        run: ./gradlew build
      - name: ArchUnit Test
        run: ./gradlew test --tests '*ArchitectureTest*'

  docker:
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Build & Push Docker Image
        run: |
          docker build -t ghcr.io/${{ github.repository }}/carry-app:${{ github.sha }} .
          docker push ghcr.io/${{ github.repository }}/carry-app:${{ github.sha }}

  deploy:
    needs: docker
    runs-on: ubuntu-latest
    steps:
      - name: Update Kubernetes Deployment
        run: |
          kubectl set image deployment/carry-order \
            carry-order=ghcr.io/${{ github.repository }}/carry-order:${{ github.sha }}
          # Flagger가 자동으로 카나리 배포 시작
```

### 모놀리스 단계 CI/CD

```
PR → Build & Test → Merge → Docker Image → Deploy (단일 서비스)
```

### 마이크로서비스 단계 CI/CD

```
PR → Build & Test (영향 모듈만)
   → Contract Test
   → Merge
   → Docker Image (변경된 서비스만)
   → Deploy (Flagger 카나리)
   → 메트릭 평가
   → 승격 or 롤백
```

---

## 전체 여정 요약

```
 현재                     Phase 1-4                    Phase 5-6
┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────────────┐
│ V1 모놀리스      │    │ V2 모듈러 모놀리스    │    │ V2 마이크로서비스         │
│ (WebFlux, Java)  │    │ (Kotlin, JPA, Kafka) │    │ (K8s, Linkerd, Flagger) │
│                  │    │                     │    │                         │
│ 단일 JAR         │───→│ 단일 JAR             │───→│ 서비스별 컨테이너         │
│ 단일 DB          │    │ 단일 DB, 프리픽스     │    │ 서비스별 DB              │
│ 동기 호출        │    │ Kafka 이벤트          │    │ Kafka 이벤트 (동일)      │
│ 모니터링 없음     │    │ OTel + Jaeger        │    │ OTel + Jaeger + Linkerd │
│ 수동 배포        │    │ GitHub Actions       │    │ Flagger 카나리 배포      │
│ 테스트 6개       │    │ Unit + Integration   │    │ + Contract + Chaos      │
└─────────────────┘    └─────────────────────┘    └─────────────────────────┘
```

---

> 본 문서는 프로젝트 진행에 따라 지속적으로 업데이트된다.
