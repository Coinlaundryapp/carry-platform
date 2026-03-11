# 03. 아키텍처 원칙 및 모듈 구조

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 아키텍처 원칙

1. **모듈 자율성**: 각 모듈은 자체 도메인 모델, 저장소, API를 소유한다. 다른 모듈의 테이블을 직접 조회하지 않는다.
2. **이벤트 기반 상태 전파**: 모듈 간 상태 변경은 반드시 Kafka 이벤트로 전파한다. Spring `ApplicationEvent`를 모듈 간 통신에 사용하지 않는다.
3. **인터페이스 경계**: 모듈 간 동기 호출이 필요한 경우 Outbound Port 인터페이스를 통해서만 접근한다.
4. **단일 DB, 논리적 분리**: 하나의 PostgreSQL 인스턴스를 사용하되, 모듈별 테이블 프리픽스로 소유권을 명확히 한다.
5. **Outbox 패턴 강제**: 도메인 이벤트 발행은 반드시 Transactional Outbox를 통해 이루어진다. 직접 Kafka 발행 금지 (dual-write 방지).
6. **관측 가능성 내장**: 모든 모듈은 OTel 자동 계측을 통해 트레이스를 생성하며, Kafka 메시지에 TraceContext를 전파한다.

---

## Gradle 멀티 모듈 구조

```
carry-backend/
├── settings.gradle.kts
├── build.gradle.kts                         # 루트: Kotlin, Java 21, 공통 플러그인
├── docker-compose.yml                       # 로컬 개발 인프라
│
├── carry-common/                            # 공통 유틸, 예외, API 응답 포맷
├── carry-event/                             # 크로스 모듈 이벤트 계약 (순수 데이터 클래스)
│
├── carry-infra-kafka/                       # Kafka Producer/Consumer 공통 설정, Outbox 공통
├── carry-infra-persistence/                 # JPA 공통 (BaseEntity, Auditing, QueryDSL)
├── carry-infra-redis/                       # Redis 설정, 캐시 추상화
├── carry-infra-s3/                          # S3 클라이언트 설정
├── carry-infra-observability/               # OTel, Micrometer 설정
│
├── carry-security/                          # JWT, SecurityConfig, 인증 필터
│
├── carry-order/                             # 주문 도메인
├── carry-payment/                           # 결제 도메인
├── carry-dispatch/                          # 배차 도메인
├── carry-operation/                         # 운영 도메인
├── carry-user/                              # 회원 도메인
├── carry-laundromat/                        # 세탁소 도메인
├── carry-review/                            # 리뷰 도메인
├── carry-notification/                      # 알림 도메인
├── carry-geo/                               # 위치 도메인
├── carry-media/                             # 미디어 도메인
├── carry-price/                             # 가격 도메인
├── carry-service-availability/              # 서비스 가용성 도메인
│
└── carry-app/                               # @SpringBootApplication, 모듈 조립, 설정
```

### 모듈 의존성 규칙

```
carry-app  ──→  모든 도메인 모듈  ──→  carry-common
                                  ──→  carry-event
                                  ──→  필요한 carry-infra-* 모듈

carry-event  ──→  (의존성 없음, 순수 Kotlin data class)

도메인 모듈 ──✗──→ 다른 도메인 모듈  (직접 의존 금지)
```

- `carry-event`는 Spring 의존성이 없는 순수 데이터 모듈이다. 모든 도메인 모듈이 이벤트 스키마를 공유하기 위해 참조한다.
- 도메인 모듈은 다른 도메인 모듈을 직접 import할 수 없다. Outbound Port를 통해서만 접근하며, 실제 연결은 `carry-app`에서 조립된다.

### 모듈 역할 분류

| 분류 | 모듈 | 설명 |
|------|------|------|
| **계약** | carry-event | 모듈 간 이벤트 스키마. 순수 Kotlin data class, Spring 무의존 |
| **공통** | carry-common | 예외, API 응답, 유틸리티 |
| **인프라** | carry-infra-* | 기술 관심사 (Kafka, JPA, Redis, S3, OTel) |
| **보안** | carry-security | JWT, OAuth2, SecurityConfig |
| **도메인** | carry-order, payment, dispatch, ... | 비즈니스 로직 |
| **조립** | carry-app | SpringBootApplication, 모든 모듈 조립, 설정 파일 |

---

## 도메인 모듈 내부 구조

각 도메인 모듈은 Hexagonal Architecture (Ports & Adapters) 패턴을 따른다.

```
carry-order/
└── src/main/kotlin/com/carry/order/
    ├── domain/
    │   ├── model/              # Aggregate Root, Entity, Value Object (@Entity)
    │   ├── event/              # 모듈 내부 도메인 이벤트
    │   ├── service/            # 도메인 서비스 (순수 비즈니스 로직)
    │   └── repository/         # Repository 인터페이스 (Port)
    │
    ├── application/
    │   ├── service/            # 유스케이스 오케스트레이션
    │   ├── port/
    │   │   ├── inbound/        # 유스케이스 인터페이스 (다른 모듈이 호출할 진입점)
    │   │   └── outbound/       # 외부 의존 인터페이스 (다른 모듈 데이터 조회)
    │   └── dto/                # Command / Query DTO
    │
    ├── infrastructure/
    │   ├── persistence/        # JPA Repository 구현, QueryDSL
    │   ├── messaging/
    │   │   ├── producer/       # Outbox 이벤트 저장
    │   │   └── consumer/       # Kafka 이벤트 리스너
    │   └── adapter/            # Outbound Port 구현 (모놀리스: 직접 호출 / 분리 시: HTTP)
    │
    └── presentation/
        ├── rest/               # REST Controller
        └── payload/
            ├── request/
            └── response/
```

### 계층 간 의존 방향

```
presentation → application → domain ← infrastructure
                    ↑                       |
                    └───────────────────────┘
                    (Port 인터페이스를 통한 역전)
```

- `domain`은 어떤 외부 프레임워크에도 의존하지 않는다 (JPA 어노테이션은 실용적 예외).
- `infrastructure`는 `domain`의 Repository 인터페이스를 구현한다.
- `application`은 `domain`과 Outbound Port를 조합하여 유스케이스를 오케스트레이션한다.

### 마이크로서비스 전환 시 모듈 구조 변화

모듈러 모놀리스에서 마이크로서비스로 분리할 때, 내부 패키지 구조는 **그대로 유지**된다.
변경되는 것은 `infrastructure/adapter/` 내의 구현체뿐이다:

```
# 모놀리스일 때
infrastructure/adapter/LaundromatQueryAdapter.kt
  → LaundromatQueryService 빈을 직접 주입

# 마이크로서비스로 분리 후
infrastructure/adapter/LaundromatQueryAdapter.kt
  → HTTP Client로 carry-laundromat 서비스 호출
```
