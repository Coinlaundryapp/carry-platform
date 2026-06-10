# 도메인 매핑 정리 — 집중형 VO 추출 + updateFrom 컬렉션 통일

- 작성일: 2026-06-10
- 대상 빚: ROADMAP Phase 6.4 `updateFrom() 일관성 통일` + 메모리 "reconstitute 18-param→VO" 기술부채
- 브랜치: `refactor/reconstitute-vo` (base: `develop`)

## 1. 배경

carry-platform은 ADR-0001(도메인/JPA 분리)에 따라 각 애그리거트가
`도메인 모델`(불변/팩토리 `create`·`reconstitute`)과 `JpaEntity`(`toDomain`·`updateFrom`·`fromDomain`)로
나뉜다. 17개 애그리거트 전수 조사 결과 두 종류의 빚이 확인됐다.

1. **긴 reconstitute 파라미터 + 평면 원시값 클러스터** — 일부 애그리거트가 응집된 원시값을
   VO로 묶지 않고 평면 나열한다. 가장 두드러진 곳:
   - `Notification.reconstitute` 13개 파라미터 — `title`/`content`(메시지 본문),
     `referenceType`/`referenceId`(참조 대상)가 평면.
     (`carry-notification/.../domain/model/Notification.kt:54`)
   - `ShippingAddress.reconstitute` 12개 파라미터 — `recipientName`/`recipientPhone`(수령인)가 평면.
     `Address`·`Coordinates`는 이미 VO이나 수령인만 누락.
     (`carry-user/.../domain/model/ShippingAddress.kt:97`)
2. **updateFrom 컬렉션 처리 관용구 불일치** — 자식 컬렉션을 "전체 교체"하는 엔티티들이
   `clear()+forEach add` / `clear()+addAll(map)` 등 미묘하게 다른 코드로 같은 일을 한다.
   (`ReviewJpaEntity.kt:53`, `LaundromatJpaEntity.kt:72`, `ServiceAreaJpaEntity.kt:49`,
   `PricePolicyJpaEntity.kt:59`, `DeliveryStepJpaEntity.kt:52`)

> 참고: `Order.reconstitute`(17개)가 파라미터 수 최다이나 이미 `OrderShippingAddress`·
> `OrderCancellation` VO로 묶여 있어 추가 그룹화 여지가 낮다. 본 작업의 대상이 아니다.

### 의도적으로 다루지 않는 것 (정확성 근거)

`Delivery.updateFrom`(`DeliveryJpaEntity.kt:65`)은 자식(steps)을 **식별자 보존 방식**으로 갱신한다
(`stepType`으로 기존 엔티티를 찾아 `existingStep.updateFrom()` 위임). 이는 "전체 교체"와 의미가
**다르다** — `clear()+add`는 자식을 삭제 후 재삽입(새 PK·`@Version` 리셋)하지만, 식별자 보존은
자식 행 정체성과 낙관적 락을 유지한다. 따라서 컬렉션 통일 대상에서 **제외**하며, 코드에 차이를 주석으로 남긴다.

## 2. 목표 / 비목표

### 목표
- 응집된 원시값 클러스터를 **진짜 도메인 VO**로 묶어 reconstitute 파라미터를 줄이고 불변을 강화한다.
- "전체 교체" updateFrom 관용구를 **단일 헬퍼**로 통일한다(동작 변경 없음).
- 행동(behavior)은 보존한다 — 순수 구조 리팩터링. DB 스키마/컬럼 변경 없음, 마이그레이션 불요.

### 비목표 (이번 PR 범위 밖)
- `Dispatch`/`Payment`/`Order` 추가 파라미터 그룹화.
- `ShippingAddress.entranceInfo`/`areaCode` VO화(응집도 낮음 — 수령인이 아니라 주소·배달 힌트).
- `Delivery`의 식별자 보존 패턴 변경.
- 모듈 간 공유 VO(예: carry-user와 carry-order의 수령인 통합) — 바운디드 컨텍스트 경계 사안, 별도.
- `Phone`/`Email` 등 기존 VO를 `Recipient`에 도입(검증 의미 변경 위험).

## 3. 상세 설계

### Part A — VO 추출

#### A-1. carry-notification

신규 VO (`carry-notification/.../domain/vo/`):

```kotlin
@JvmInline
value class NotificationMessage private constructor(...)  // 또는 data class
// 결정: data class. title/content 두 필드라 value class 불가.
data class NotificationMessage(val title: String, val content: String)

data class NotificationReference(val type: String, val id: Long)
```

- `Notification`(도메인): `title`/`content` → `message: NotificationMessage`,
  `referenceType`/`referenceId` → `reference: NotificationReference?`.
  - **불변 강화**: 현재 `referenceType: String?` + `referenceId: Long?`가 독립 nullable이라
    한쪽만 채워진 부정합 상태가 표현 가능하다. `reference: NotificationReference?`는 "둘 다 또는 없음"을
    타입으로 강제한다.
  - 노출 프로퍼티 하위호환: 필요한 호출부가 `notification.title` 등을 쓰면
    `val title get() = message.title` 위임 프로퍼티로 보존(호출부 변경 최소화). 실제 사용처 확인 후 결정.
- `create`/`reconstitute` 시그니처: 평면 4개 → VO 2개.
- `NotificationJpaEntity`: **컬럼은 평면 유지**(`title`,`content`,`referenceType`,`referenceId`).
  - `toDomain`: `message = NotificationMessage(title, content)`,
    `reference = referenceType?.let { NotificationReference(it, referenceId!!) }`.
  - `fromDomain`: VO 분해해 컬럼 채움.
  - `updateFrom`: 해당 필드는 불변(생성 후 변경 없음)이라 **무관** — 기존대로 status/sentAt/failReason만.
- 호출부: `NotificationCommandService`(`create` 호출), `NotificationTest`(`reconstitute`/`create`).

#### A-2. carry-user

신규 VO (`carry-user/.../domain/vo/Recipient.kt`):

```kotlin
data class Recipient(val name: String, val phone: String) {
    init {
        requireInput(name.isNotBlank()) { "수령인 이름은 비어있을 수 없습니다" }
        requireInput(phone.isNotBlank()) { "수령인 전화번호는 비어있을 수 없습니다" }
    }
}
```

- 검증 **중앙화**: 현재 동일 `isNotBlank` 검증이 `ShippingAddress.create()`(78–79행)와
  `update()`(42–43행)에 중복. VO `init`로 단일화.
- `ShippingAddress`(도메인): `_recipientName`/`_recipientPhone` → `_recipient: Recipient`.
  `create`/`update`/`reconstitute` 시그니처에서 두 파라미터 → `recipient: Recipient`.
  - 하위호환 위임: `val recipientName get() = _recipient.name`, `val recipientPhone get() = _recipient.phone`
    (엔티티 매핑·호출부가 개별 필드를 참조하면 보존).
- `ShippingAddressJpaEntity`: 컬럼 평면 유지. `toDomain`에서 `Recipient(recipientName, recipientPhone)` 조립,
  `updateFrom`/`fromDomain`에서 `address.recipient.name`/`.phone` 분해.
  - 주의: `recipientName`/`recipientPhone`은 **가변**(update() 경로) → `updateFrom`이 실제로 분해 대입한다.
- `areaCode` 검증(`isNotBlank`)은 `ShippingAddress`에 그대로 둔다(수령인 VO 책임 아님).
- 호출부: `ShippingAddressService`, `ShippingAddressTest`.

### Part B — updateFrom 컬렉션 통일

신규 확장함수 (`carry-infra-persistence/.../persistence/`):

```kotlin
/**
 * 자식 컬렉션을 도메인 소스로 "전체 교체"한다(clear 후 transform 결과 추가).
 * orphanRemoval=true 자식의 기존 관용구(clear+add)와 동작이 동일하다.
 * 식별자 보존이 필요한 경우(예: Delivery.steps)에는 사용하지 말 것.
 */
fun <E, S> MutableList<E>.replaceAllFrom(source: Iterable<S>, transform: (S) -> E) {
    clear()
    source.forEach { add(transform(it)) }
}
```

적용 대상(동작 불변 치환):

| 엔티티 | 컬렉션 | 현재 | file |
|---|---|---|---|
| ReviewJpaEntity | mediaList | clear()+forEach add | `ReviewJpaEntity.kt:53` |
| LaundromatJpaEntity | options, mediaResources | clear()+addAll | `LaundromatJpaEntity.kt:72` |
| ServiceAreaJpaEntity | schedules, holidays | clear()+addAll(map) | `ServiceAreaJpaEntity.kt:49` |
| PricePolicyJpaEntity | optionPrices | clear()+addAll(map) | `PricePolicyJpaEntity.kt:59` |
| DeliveryStepJpaEntity | media | clear()+forEach add | `DeliveryStepJpaEntity.kt:52` |

- 자식 생성 시 부모 역참조가 필요한 경우 `transform` 람다가 `this`(부모 엔티티)를 캡처한다.
- `DeliveryJpaEntity.updateFrom`은 **변경하지 않으며**, 식별자 보존 이유를 주석으로 명시한다.

## 4. 테스트 계획 (TDD: RED → GREEN → REFACTOR)

신규 단위 테스트:
- `NotificationMessage`/`NotificationReference`: 생성·동등성. `NotificationReference`는 type/id 동반 강제.
- `Recipient`: blank 이름/전화 거부(`requireInput` 예외), 정상 생성.
- `replaceAllFrom`: 비어있지 않은 리스트를 새 소스로 교체, 빈 소스로 비움, transform 적용 확인.

기존 테스트 반영(시그니처 변경):
- `NotificationTest`, `ShippingAddressTest`: `create`/`reconstitute` 호출을 VO 인자로 갱신, 기존 단언 GREEN 유지.
- 엔티티 매핑 라운드트립: 대상 엔티티에 `fromDomain(d).toDomain()`이 원본과 동치임을 확인하는 테스트가
  없으면 보강(있으면 시그니처만 반영).
- `NotificationCommandService`/`ShippingAddressService` 및 관련 서비스 테스트 GREEN 유지.

## 5. 검증

- 영향 모듈 `:carry-notification:test`, `:carry-user:test`, `:carry-review:test`,
  `:carry-laundromat:test`, `:carry-service-availability:test`, `:carry-price:test`,
  `:carry-delivery:test`, `:carry-infra-persistence:test`, 그리고 `:carry-app:test`(Testcontainers IT).
- `BUILD SUCCESSFUL`만 신뢰하지 말고 `build/test-results/**/*.xml`의 tests/failures 수로 통과 검증.
- JDK 21로 빌드(`org.gradle.java.home` 미커밋 유지).

## 6. 리스크 / 완화

| 리스크 | 완화 |
|---|---|
| 컬렉션 헬퍼가 식별자 보존 케이스에 오용 | KDoc 경고 + Delivery 제외 주석. 적용 대상은 모두 기존 clear+add. |
| VO 도입이 위임 프로퍼티 누락으로 호출부 깨짐 | 하위호환 위임 프로퍼티 제공, 컴파일·테스트로 전수 확인. |
| `NotificationReference` 도입이 한쪽-only 레거시 데이터와 충돌 | 코드 경로상 항상 동반 세팅(현재도 사실상). DB 변경 없음. 매핑은 `referenceType?` 기준 분기. |
| 행동 변경 혼입 | 순수 구조 리팩터링 원칙. 매핑 라운드트립 테스트로 동치 보장. |

## 7. 범위 밖 후속(메모)
`Dispatch`/`Payment` 그룹화, 모듈 간 수령인 VO 통합, `Delivery` 패턴 재검토는 별도 작업으로 남긴다.
