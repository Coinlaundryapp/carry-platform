# 02. 기술 스택 결정 및 근거

> 최종 수정일: 2026-03-11
> 상태: Draft

---

## 최종 기술 스택

| 영역 | 기술 | 버전 |
|------|------|------|
| 언어 | Kotlin | 2.x |
| 플랫폼 | Java | 21 (Virtual Threads) |
| 프레임워크 | Spring Boot | 3.4.x |
| 웹 | Spring MVC | (WebFlux 제거) |
| ORM | Spring Data JPA (Hibernate) | |
| DB | PostgreSQL | 16+ |
| 이벤트 브로커 | Apache Kafka | |
| CDC | Debezium (Kafka Connect) | |
| 캐시 | Redis | |
| 파일 저장 | AWS S3 | |
| 인증 | JWT + Kakao OAuth2 | |
| 추적 | OpenTelemetry + Jaeger | |
| 서비스 메시 | Linkerd | |
| 배포 전략 | Flagger (카나리 배포) | |
| 컨테이너 오케스트레이션 | Kubernetes | |
| 빌드 | Gradle (Kotlin DSL) | 8.x |
| 컨테이너 | Docker + Docker Compose | |

---

## 결정 근거

### WebFlux → Spring MVC + Virtual Threads

WebFlux는 높은 동시성이 필요한 I/O 바운드 애플리케이션에 적합하지만,
도메인 서비스 개발에서는 다음과 같은 문제가 있었다:

- **디버깅**: 리액티브 체인에서 에러 발생 시 스택 트레이스가 실질적으로 무의미
- **학습 곡선**: `Mono`/`Flux` 연산자 조합이 복잡한 비즈니스 로직과 맞물리면 코드 리뷰와 유지보수가 어려움
- **트랜잭션**: R2DBC의 트랜잭션 관리가 JPA 대비 번거롭고, Aggregate Root 패턴 지원이 부족
- **디버깅 모드 패널티**: 리액티브 디버깅 옵션을 켜면 성능 이점이 감소 — 그렇다면 동기식이 더 합리적

Java 21의 Virtual Threads는 **플랫폼 레벨에서 논블로킹 I/O의 이점을 제공**하면서도
코드는 전통적인 동기식으로 유지할 수 있다. 별도 코드 변경 없이 `spring.threads.virtual.enabled=true` 한 줄로 적용 가능하다.

### R2DBC → JPA (Hibernate)

- Aggregate Root 패턴을 자연스럽게 지원 (`@OneToMany`, `CascadeType`, orphan removal)
- Lazy Loading으로 불필요한 연관 데이터 로딩 방지
- QueryDSL 또는 JPQL로 복잡한 쿼리 작성 용이
- 방대한 생태계와 레퍼런스

### Java → Kotlin

- Null safety로 NullPointerException 방지
- `data class`로 DTO/이벤트 정의가 간결
- 확장 함수, scope 함수로 코드 가독성 향상
- Spring Boot의 공식 Kotlin 지원

### 단일 서비스 분리 → 모듈러 모놀리스

- 하나의 배포 단위에서 모듈 경계를 Gradle 멀티 모듈로 강제
- 모듈 간 이벤트 통신은 Kafka를 사용하여 **물리 분리 시 코드 변경 없음**
- 인프라 복잡도를 최소화하면서 도메인 개발에 집중 가능
- 모듈 간 직접 의존은 인터페이스(Port)로만 허용

### 비동기 통신: 코드 레벨이 아닌 인프라 레벨로

| 관점 | WebFlux (코드 레벨) | Kafka + Debezium (인프라 레벨) |
|------|---------------------|-------------------------------|
| 비동기 처리 위치 | 애플리케이션 코드 전체 | 모듈 경계의 이벤트 발행/소비만 |
| 디버깅 난이도 | 매우 높음 | 일반 동기 코드 수준 |
| 코드 복잡도 | 높음 (Mono/Flux 체이닝) | 낮음 (이벤트 핸들러만 비동기) |
| 성능 | 리액티브 스레드 풀 | Virtual Threads + 이벤트 기반 |
| 서비스 분리 대응 | 코드 전면 수정 필요 | 인프라 설정만 변경 |

### Linkerd (서비스 메시)

Istio 대비 선택한 이유:
- **경량**: 사이드카 프록시(linkerd2-proxy)가 Rust로 작성되어 리소스 소비가 적음
- **단순성**: 복잡한 설정 없이 mTLS, 트래픽 메트릭, 리트라이 정책 제공
- **OTel 호환**: Linkerd가 생성하는 메트릭을 OTel Collector로 수집 가능
- **Flagger 연동**: 카나리 배포를 위한 Flagger와 네이티브 통합

### Flagger (카나리 배포)

- Linkerd 메트릭 기반으로 카나리 버전의 성공률/레이턴시를 자동 평가
- 기준 미달 시 자동 롤백
- 점진적 트래픽 이동 (5% → 10% → 30% → 100%)
- GitOps 워크플로우와 자연스러운 통합
