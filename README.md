<p align="center">
  <img src="docs/assets/wave-bubble.svg" width="180" alt="Carry Logo" />
</p>

<h1 align="center">Carry Platform</h1>

<p align="center">
  <strong>코인 세탁 배달 O2O 서비스 백엔드</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Kafka-CDC-231F20?logo=apachekafka&logoColor=white" alt="Kafka" />
  <img src="https://img.shields.io/badge/OpenTelemetry-Observability-000000?logo=opentelemetry&logoColor=white" alt="OTel" />
</p>

<p align="center">
  고객이 세탁물을 맡기면, 배달원이 수거하고, 세탁소에서 세탁 후 다시 배달하는<br/>
  풀사이클 O2O 세탁 배달 서비스의 백엔드 플랫폼입니다.
</p>

---

## 목차

- [아키텍처](#아키텍처)
- [기술 스택](#기술-스택)
- [모듈 구조](#모듈-구조)
- [주문 사가 플로우](#주문-사가-플로우)
- [시작하기](#시작하기)
- [API 문서](#api-문서)
- [프로젝트 구조](#프로젝트-구조)
- [로드맵](#로드맵)

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        carry-app (Bootstrap)                        │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │  Order   │ │ Payment  │ │ Dispatch │ │ Delivery │ │   User   │ │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ │
│  ┌────┴─────┐ ┌────┴─────┐ ┌────┴─────┐ ┌────┴─────┐ ┌────┴─────┐ │
│  │Laundromat│ │  Price   │ │   Geo    │ │  Review  │ │  Media   │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │
│  ┌──────────┐ ┌───────────────────┐                                │
│  │Operation │ │Service Availability│                                │
│  └──────────┘ └───────────────────┘                                │
├─────────────────────────────────────────────────────────────────────┤
│  carry-common │ carry-event │ carry-security                        │
├─────────────────────────────────────────────────────────────────────┤
│  carry-infra-persistence │ carry-infra-kafka │ carry-infra-redis    │
│  carry-infra-s3          │ carry-infra-observability                │
├─────────────────────────────────────────────────────────────────────┤
│  PostgreSQL  │  Kafka + Debezium  │  Redis  │  S3  │  Jaeger       │
└─────────────────────────────────────────────────────────────────────┘
```

### 핵심 설계 원칙

| 원칙 | 설명 |
|------|------|
| **Modular Monolith** | 도메인별 독립 모듈, 마이크로서비스 전환을 전제한 설계 |
| **Hexagonal Architecture** | 각 모듈은 Port & Adapter 패턴으로 외부 의존성 격리 |
| **Transactional Outbox** | 이벤트 발행은 반드시 Outbox 테이블을 경유 (dual-write 방지) |
| **Choreography Saga** | 중앙 오케스트레이터 없이 이벤트 기반 분산 트랜잭션 |
| **Bounded Context** | 모듈 간 직접 참조 금지, 동기 조회는 Port 인터페이스로만 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Language** | Kotlin 2.x, Java 21 |
| **Framework** | Spring Boot 3.4, Spring MVC + Virtual Threads |
| **Persistence** | JPA (Hibernate), Flyway |
| **Database** | PostgreSQL 16+ |
| **Messaging** | Apache Kafka, Debezium CDC |
| **Cache** | Redis |
| **Storage** | AWS S3 |
| **Auth** | JWT + Kakao OAuth2 |
| **Observability** | OpenTelemetry, Jaeger |
| **Infra** | Docker Compose, Kubernetes, Linkerd, Flagger |
| **Build** | Gradle Kotlin DSL |
| **Docs** | springdoc-openapi (Swagger UI) |

---

## 모듈 구조

### Foundation

| 모듈 | 설명 |
|------|------|
| `carry-common` | 공통 응답 래퍼, 예외 체계, 유틸리티 |
| `carry-event` | 도메인 이벤트 스키마 정의 |
| `carry-security` | JWT 인증, OAuth2 설정 |

### Infrastructure

| 모듈 | 설명 |
|------|------|
| `carry-infra-persistence` | JPA 공통 설정, Auditing |
| `carry-infra-kafka` | Kafka Producer/Consumer 설정 |
| `carry-infra-redis` | Redis 클라이언트 설정 |
| `carry-infra-s3` | AWS S3 클라이언트 설정 |
| `carry-infra-observability` | OpenTelemetry + Micrometer 설정 |

### Domain

| 모듈 | 설명 | 주요 API |
|------|------|----------|
| `carry-user` | 사용자 프로필, 배송지 관리 | `GET /api/v1/users/me`, `POST /api/v1/shipping-addresses` |
| `carry-laundromat` | 세탁소 등록, 주변 검색 | `GET /api/v1/laundromats`, `POST /api/v1/laundromats` |
| `carry-price` | 가격 정책, 금액 계산 | `GET /api/v1/prices`, `POST /api/v1/prices/calculate` |
| `carry-geo` | 지오코딩, 역지오코딩 | `GET /api/v2/geo/geocode`, `GET /api/v2/geo/reverse-geocode` |
| `carry-order` | 주문 생성/취소, 상태 관리 | `POST /api/v2/orders`, `GET /api/v2/orders/my` |
| `carry-payment` | 결제 요청, 청구서 관리 | `POST /api/v2/payments/pay`, `GET /api/v2/payments/{orderId}/invoice` |
| `carry-dispatch` | 배차 배정/선점/수락 | `POST /api/v2/dispatches/{id}/claim`, `POST /api/v2/dispatches/{id}/accept` |
| `carry-delivery` | 수거~배달 프로세스 관리 | `POST /api/v2/deliveries/{id}/pickup`, `POST /api/v2/deliveries/{id}/delivery` |
| `carry-review` | 리뷰 작성, 통계 | `POST /api/v2/reviews`, `GET /api/v2/reviews/laundromat/{id}/statistics` |
| `carry-notification` | 알림 발송/조회 | `GET /api/v2/notifications/my` |
| `carry-media` | 미디어 파일 업로드/다운로드 | `POST /api/v2/media/upload/{folder}`, `GET /api/v2/media/{accessKey}/download` |
| `carry-operation` | 운영 대시보드, 이용약관 | `GET /api/v2/admin/dashboard/summary`, `GET /api/v2/terms` |
| `carry-service-availability` | 서비스 권역, 운영시간 관리 | 내부 모듈 (API 미노출) |

### Assembly

| 모듈 | 설명 |
|------|------|
| `carry-app` | Spring Boot 메인 애플리케이션, 모듈 조립, 크로스모듈 어댑터 |

---

## 주문 사가 플로우

```
Customer                Order              Payment            Dispatch           Delivery
   │                      │                    │                   │                  │
   ├── 주문 생성 ──────────►│                    │                   │                  │
   │                      ├── OrderCreated ────►│                   │                  │
   │                      │                    ├── 결제 대기        │                  │
   │                      │                    │                   │                  │
   ├── 결제 요청 ──────────────────────────────►│                   │                  │
   │                      │                    ├── PaymentCompleted►│                  │
   │                      ◄── OrderPaid ───────┤                   │                  │
   │                      ├── OrderPaid ───────────────────────────►│                  │
   │                      │                    │                   ├── 배차 생성       │
   │                      │                    │                   ├── 배달원 배정     │
   │                      │                    │                   ├── DispatchAssigned►│
   │                      │                    │                   │                  ├── 수거
   │                      │                    │                   │                  ├── 세탁
   │                      │                    │                   │                  ├── 건조
   │                      │                    │                   │                  ├── 배달
   │                      ◄── DeliveryCompleted─────────────────────────────────────┤
   │                      ├── 주문 완료        │                   │                  │
```

### 주문 상태 머신

```
CREATED ──► PAYMENT_PENDING ──► PAID ──► DISPATCHED ──► DELIVERED
                │                 │           │
                ▼                 ▼           ▼
          PAYMENT_FAILED    REFUND_PENDING  DISPATCH_FAILED
                                  │
                                  ▼
                           REFUND_COMPLETED
```

---

## 시작하기

### 사전 요구사항

- Java 21+
- Docker & Docker Compose

### 인프라 실행

```bash
docker compose up -d
```

PostgreSQL, Kafka, Zookeeper, Redis, Debezium Connect, Jaeger, OTel Collector가 실행됩니다.

### 애플리케이션 실행

```bash
./gradlew :carry-app:bootRun
```

### 빌드 & 테스트

```bash
# 전체 빌드
./gradlew build

# 테스트만 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :carry-order:test
```

---

## API 문서

애플리케이션 실행 후 Swagger UI에서 전체 API를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```

모든 API는 JWT Bearer 토큰 인증이 필요합니다. Swagger UI 상단의 **Authorize** 버튼으로 토큰을 설정하세요.

### API 그룹

| 태그 | 설명 |
|------|------|
| User | 사용자 프로필 관리 |
| Shipping Address | 배송지 CRUD |
| Laundromat | 세탁소 등록/검색 |
| Price | 가격 정책/계산 |
| Geocoding | 주소-좌표 변환 |
| Order | 주문 생성/취소/조회 |
| Payment | 결제/청구서 |
| Dispatch - Carrier | 배달원 배차 |
| Dispatch - Admin | 관리자 배차 |
| Carrier Area | 배달원 권역 |
| Delivery | 배달 프로세스 |
| Review | 리뷰/통계 |
| Notification | 알림 |
| Media | 미디어 파일 |
| Terms | 이용약관 |
| Dashboard | 운영 대시보드 |

---

## 프로젝트 구조

각 도메인 모듈은 Hexagonal Architecture를 따릅니다.

```
carry-{module}/
└── src/main/kotlin/com/carry/{module}/
    ├── domain/                  # 순수 도메인 레이어
    │   ├── model/               #   엔티티, 애그리게이트
    │   ├── vo/                  #   값 객체
    │   ├── exception/           #   도메인 예외
    │   └── event/               #   도메인 이벤트
    ├── application/             # 애플리케이션 레이어
    │   ├── port/
    │   │   ├── inbound/         #   유스케이스 인터페이스
    │   │   └── outbound/        #   외부 의존 인터페이스
    │   └── service/             #   유스케이스 구현
    ├── adapter/
    │   ├── inbound/
    │   │   └── rest/            #   REST 컨트롤러, DTO
    │   └── outbound/
    │       ├── persistence/     #   JPA Repository
    │       └── messaging/       #   Kafka Consumer/Producer
    └── infrastructure/          # 모듈 내부 설정
```

### 모듈 간 통신 규칙

```
                ┌──────────────┐         ┌──────────────┐
                │   Module A   │         │   Module B   │
                │              │         │              │
  동기 조회 ──► │ Outbound Port├────────►│ Inbound Port │  (읽기 전용)
                │              │         │              │
  상태 변경 ──► │ Outbox Table ├──CDC──►│ Kafka Consumer│  (이벤트 기반)
                └──────────────┘         └──────────────┘
```

- **읽기**: Outbound Port 인터페이스를 통한 동기 호출
- **쓰기**: Transactional Outbox + Debezium CDC를 통한 비동기 이벤트
- **금지**: 타 모듈의 테이블/서비스 직접 참조

---

## 로드맵

| Phase | 내용 | 상태 |
|-------|------|------|
| **Phase 1** | 프로젝트 기반 구축 (Gradle, Docker, CI/CD) | Done |
| **Phase 2** | 핵심 도메인 (User, Laundromat, Price, Geo) | Done |
| **Phase 3** | 주문 사가 (Order, Payment, Dispatch + CDC + OTel) | Done |
| **Phase 4** | 부가 도메인 (Operation, Review, Notification, Media) | Done |
| **Phase 5** | 마이크로서비스 전환 (K8s, DB 분리, API Gateway) | Planned |
| **Phase 6** | 서비스 메시 + 카나리 배포 (Linkerd, Flagger) | Planned |

### 마이크로서비스 분리 순서

```
1. Notification  ──►  2. Media  ──►  3. Dispatch  ──►  4. Payment  ──►  5. Order
   (무상태, 독립)      (S3 집중)      (독립 스케일링)    (보안 요구)      (코어 도메인)
```

---

## 라이선스

Private

