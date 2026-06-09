# ADR-0001: 도메인 모델과 JPA 엔티티 분리

## 상태

Accepted

## 날짜

2026-06-09

## 맥락

carry-platform은 헥사고날(포트-어댑터) 아키텍처를 채택한 멀티모듈 Kotlin/Spring 백엔드다.
각 비즈니스 모듈(`carry-order`, `carry-payment`, `carry-delivery` 등)은 영속화가 필요한
애그리거트(`Order`, `Payment`, `Delivery` …)를 가진다.

### 문제 정의

영속성 프레임워크(JPA/Hibernate)와 도메인 모델의 관계를 어떻게 둘 것인가.
가장 흔한 선택지는 도메인 클래스에 직접 `@Entity`를 붙여 하나의 객체가
"비즈니스 규칙 + ORM 매핑"을 겸하게 하는 것이다. 이 방식은 보일러플레이트가 적지만,
도메인 모델이 다음에 오염된다.

- ORM 요구사항(인자 없는 생성자, 가변 필드, 지연로딩 프록시, `@Version` 등)이 도메인에 침투
- 도메인 단위 테스트가 JPA/Spring 컨텍스트 없이는 불가능하거나 부자연스러움
- 영속성 스키마 변경이 도메인 불변식 표현을 제약

### 제약

- 도메인 로직의 결정성·테스트 용이성이 이 프로젝트의 1차 목표(분산 시스템·패턴 시연)
- Kotlin: `val` 불변 필드·private 생성자·팩토리로 불변식을 강하게 표현하고 싶음
- 옵티미스틱 락(`@Version`)·낙관적 동시성 제어는 영속성 계층의 책임으로 두고 싶음

## 결정

**도메인 모델과 JPA 엔티티를 물리적으로 분리한다.** 도메인 모델은 어떤 영속성/프레임워크
어노테이션도 갖지 않는 순수 Kotlin 클래스로 두고, 별도의 JPA 엔티티 클래스가 매핑을 전담한다.

### 핵심 결정 사항

1. **도메인 모델은 `domain.model` 패키지의 순수 Kotlin** — private 생성자 + `create()`(신규)
   / `reconstitute()`(영속 데이터로부터 복원) 두 팩토리. `jakarta.persistence.*`·`org.springframework.*`
   import 0건. 예: `carry-order/.../domain/model/Order.kt` (`create()` / `reconstitute()`),
   동일 패턴이 `User`·`Payment`·`Delivery`·`Dispatch`에 일관 적용.
2. **JPA 엔티티는 `adapter.outbound.persistence.entity` 패키지** — `@Entity`·`@Table`·`@Version`
   집중. 예: `OrderJpaEntity`는 `@Version`으로 옵티미스틱 락을 보유하나 도메인 `Order`에는 버전 개념이 없다.
3. **양방향 매핑은 JPA 엔티티가 소유** — `toDomain()`(엔티티→도메인), `fromDomain()`(신규 도메인→엔티티),
   `updateFrom()`(기존 엔티티에 도메인 상태 동기화).
4. **포트-어댑터로 경계 강제** — 애플리케이션 계층은 `OrderPersistencePort`(도메인 모델만 노출하는
   아웃바운드 포트)에만 의존하고, `OrderPersistenceAdapter`가 JPA를 구현한다.
5. **ArchUnit으로 분리를 컴파일/테스트 타임에 강제** — 각 도메인 모듈의
   `HexagonalArchitectureTest`가 "도메인 레이어는 `jakarta.persistence..`에 의존하지 않는다",
   "도메인 레이어는 Spring에 의존하지 않는다" 등을 단언한다.

### 구현 세부

`OrderPersistenceAdapter.save()`의 전형:

```kotlin
override fun save(order: Order): Order {
    val entity = if (order.id == null) {
        OrderJpaEntity.fromDomain(order)            // 신규: Domain → JPA
    } else {
        val existing = orderJpaRepository.getReferenceById(order.id)
        existing.updateFrom(order)                  // 기존: Domain → JPA 동기화
        existing
    }
    return orderJpaRepository.save(entity).toDomain()  // 반환: JPA → Domain
}
```

값 객체(VO) 처리: `OrderShippingAddress` 같은 도메인 VO는 JPA 엔티티에서 개별 컬럼으로
평탄화(flattening)되고, `InvoiceLineItem`·`DeliveryStep` 같은 컬렉션 VO는 자식 엔티티 리스트로 매핑된다.

## 결과

### 긍정적

- 도메인 단위 테스트가 JPA/Spring 부팅 없이 가능 → 빠르고 결정적
- 도메인이 ORM 요구사항(인자 없는 생성자, mutable 노출)에서 자유로워 불변식을 강하게 표현
- 옵티미스틱 락 등 영속성 관심사가 도메인을 오염시키지 않음(JPA 엔티티에 격리)
- ArchUnit 규칙으로 경계 침식(domain leakage)이 회귀 차단됨
- 향후 영속성 기술 교체(다른 ORM, 도큐먼트 DB 등) 시 어댑터만 교체

### 부정적

- 클래스 수·매핑 코드가 두 배(도메인 + JPA 엔티티 + `toDomain`/`fromDomain`/`updateFrom`)
- 매 저장/조회마다 매핑 비용(객체 변환) 발생
- 단순 CRUD 모듈에서도 동일한 분리를 강제 → 보일러플레이트

### 위험

| 위험 | 가능성 | 영향 | 완화 |
|------|--------|------|------|
| 매핑 누락·드리프트(도메인 필드 추가 시 JPA 매핑 미반영) | 중 | 중 | 크로스모듈 계약 테스트(ADR 미작성 항목, PR #93) + 모듈 통합 테스트가 매핑 검증 |
| `reconstitute()`의 인자 폭증(예: `Order` 18-param) → 가독성·실수 | 중 | 소 | 기술부채로 식별(ROADMAP 6.4), VO 묶음 도입 검토 |

## 고려한 대안

### 도메인 클래스에 `@Entity` 직접 부착 (단일 모델)

도메인 클래스가 곧 JPA 엔티티가 되는 가장 단순한 방식.

**장점:**
- 매핑 코드·클래스 수 최소
- 변환 비용 없음

**단점:**
- 도메인이 ORM 규약(인자 없는 생성자, mutable, 프록시)에 종속
- 도메인 테스트에 JPA/Spring 컨텍스트 필요
- 영속성 스키마가 도메인 표현을 제약

**기각 이유:** 도메인 로직의 결정성·프레임워크 독립성이 본 프로젝트의 1차 가치다.
보일러플레이트 비용을 감수하더라도 도메인 순수성을 택했다.

### MapStruct 등 매핑 라이브러리 도입

별도 매퍼 라이브러리로 변환 코드를 생성.

**장점:**
- 수기 매핑 코드 감소

**단점:**
- 코드 생성 의존성·디버깅 난이도 추가
- 도메인의 private 생성자/팩토리(`reconstitute`) 패턴과 매끄럽지 않음

**기각 이유:** JPA 엔티티가 `toDomain()`/`fromDomain()`을 직접 소유하는 편이
`reconstitute()` 팩토리와 자연스럽게 맞물리고, 변환 의도가 코드에 명시적으로 드러난다.

## 참조

- `carry-order/src/main/kotlin/com/carry/order/domain/model/Order.kt`
- `carry-order/src/main/kotlin/com/carry/order/adapter/outbound/persistence/entity/OrderJpaEntity.kt`
- `carry-order/src/main/kotlin/com/carry/order/application/port/outbound/OrderPersistencePort.kt`
- `carry-order/src/main/kotlin/com/carry/order/adapter/outbound/persistence/OrderPersistenceAdapter.kt`
- `carry-order/src/test/kotlin/com/carry/order/architecture/HexagonalArchitectureTest.kt`
- 관련: [ADR-0003 모듈 분리 기준](0003-module-decomposition-criteria.md)
