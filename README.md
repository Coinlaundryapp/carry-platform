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
- [로드맵 · 문서](#로드맵--문서)

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
| `carry-user` | 사용자 프로필, 배송지 관리 | `GET /api/v2/users/me`, `POST /api/v2/shipping-addresses` |
| `carry-laundromat` | 세탁소 등록, 주변 검색 | `GET /api/v2/laundromats`, `POST /api/v2/laundromats` |
| `carry-price` | 가격 정책, 금액 계산 | `GET /api/v2/prices`, `POST /api/v2/prices/calculate` |
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
SPRING_PROFILES_ACTIVE=local ./gradlew :carry-app:bootRun
```

> **로컬 스키마는 Flyway가 관리합니다**(`ddl-auto: validate`). 기동 시 Flyway가 `db/migration`의 V0~V23을
> 적용해 스키마를 빌드합니다(V0=PostGIS 확장 — 로컬 postgres는 `postgis/postgis` 이미지). 마이그레이션을
> 추가/수정했거나 stale 스키마를 버리고 싶으면 `scripts/db-reset.sh`(Windows: `db-reset.ps1`)로 볼륨을 비우고
> 재기동하면 Flyway가 깨끗이 다시 빌드합니다.

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

### OpenAPI 스키마 산출물

프론트엔드(`carry-app`)의 TS 타입 자동 생성이 소비하는 OpenAPI v2 스키마를 [`docs/api/openapi-v2.json`](docs/api/openapi-v2.json)으로 고정해 둡니다. 백엔드 API 변경 시 앱을 띄운 상태에서 갱신합니다:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :carry-app:bootRun   # 별도 터미널
./scripts/export-openapi.sh                                  # docs/api/openapi-v2.json 갱신
```

> 비프로덕션 로컬 인증은 `POST /api/v2/auth/dev-login {"role":"CUSTOMER|CARRIER|COORDINATOR|ADMIN"}`로 Kakao 없이 토큰을 발급받을 수 있습니다(local/dev 프로파일 전용).

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
| Dispatch - Coordinator | 코디네이터 배차 관리 |
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

## 로드맵 · 문서

엔지니어링 개선 로드맵([`ROADMAP.md`](ROADMAP.md))의 **Phase 1~7이 사실상 완료**되었다
(예외계층·장애복원력·관측성·보안·테스트 성숙도·진화관리·DX). 상세 체크리스트와 진행 노트는
`ROADMAP.md`, 설계 의사결정은 아래 ADR 참조.

- **아키텍처 의사결정**: [`docs/adr/`](docs/adr/) — ADR-0001 도메인/JPA 분리 · 0002 Outbox+CDC ·
  0003 모듈 분리 기준 · 0004 Choreography Saga · 0005 이벤트 스키마 진화 · 0006 모듈 과분해 재평가 ·
  0007 모듈러 모놀리스 유지 · 0008 도메인 이벤트 계층 없음 · 0009 규모 가정과 파생 결정
- **코드에서 역추출한 문서**: [`docs/15-invariant-catalog.md`](docs/15-invariant-catalog.md) 불변식 카탈로그(강제 수단까지 구분) ·
  [`docs/16-context-map.md`](docs/16-context-map.md) 컨텍스트 맵 · [`docs/17-ubiquitous-language.md`](docs/17-ubiquitous-language.md) 유비쿼터스 언어 사전
- **기여 가이드**: [`CONTRIBUTING.md`](CONTRIBUTING.md) — 아키텍처 규칙·3-Method 패턴·테스트 기준·커밋/PR 규약
- **클라이언트 재시도 가이드**: [`docs/14-client-retry-guide.md`](docs/14-client-retry-guide.md) — 에러 코드별 재시도 가능 여부 · `Idempotency-Key` 사용법 · 백오프 정책

### 마이크로서비스 전환 — 보류 (ADR-0007)

이 프로젝트는 **모듈러 모놀리스를 유지**한다. 분산 시스템 패턴(Outbox+CDC, Choreography Saga,
멱등 소비, 강제된 경계)은 단일 배포 단위에서 이미 시연되며, 1인·비프로덕션 맥락에서 실제 분리는
이득 대비 비용이 크다. 다만 트리거(트래픽 격차·팀 분리·규제 격리·클라우드 준비) 도달 시
**저비용 분리가 가능한 seam**을 ADR-0007에 명시해 두었다:

```
가장 분리하기 쉬운 순서(필요 시):  Notification(순수 이벤트 소비)  ──►  Payment(이벤트 + 단일 QueryPort)
```

---

## 라이선스

Private

