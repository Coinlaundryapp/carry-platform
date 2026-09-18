# 도메인 매핑 정리 (집중형 VO + updateFrom 컬렉션 통일) Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 응집된 원시값 클러스터를 진짜 도메인 VO로 묶고(Notification·ShippingAddress), updateFrom의 "전체 교체" 컬렉션 관용구를 단일 헬퍼로 통일한다. 순수 구조 리팩터링 — 행동/DB 스키마 무변경.

**Architecture:** ADR-0001(도메인/JPA 분리) 유지. VO 도입 시 **하위호환 위임 게터**(`val recipientName get() = _recipient.name` 등)를 둬서 JPA 엔티티의 `fromDomain`/`updateFrom`과 REST DTO는 무변경, 오직 팩토리(`create`/`reconstitute`) 시그니처와 그 호출부만 바뀐다. 컬렉션은 `MutableCollection<E>.replaceAllFrom` 확장함수로 통일하되 식별자 보존이 필요한 `Delivery.steps`는 제외.

**Tech Stack:** Kotlin, Spring Boot, JPA/Hibernate, JUnit5, Kotest assertions(기존 테스트 관용구 따를 것), Gradle(JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-10-domain-mapping-vo-cleanup-design.md`

**빌드/검증 규약:**
- JDK 21: `org.gradle.java.home=C:/Users/Eisen/.jdks/ms-21.0.7` (미커밋 `gradle.properties` 유지, 스테이징 금지).
- `BUILD SUCCESSFUL`만 믿지 말 것 — `build/test-results/**/*.xml`의 tests/failures 수로 확인.
- 커밋 본문 한국어(conventional prefix 영어). 커밋 메시지는 PowerShell 인용 함정 회피 위해 `git commit -F`(파일/heredoc).
- 작업 브랜치: `refactor/reconstitute-vo` (이미 생성됨, base develop).
- 단계마다 기존 테스트가 GREEN 유지되는지 확인. 새 동작 추가가 아니므로 "기존 테스트가 안전망".

**파일 구조(생성/수정 맵):**
- 생성: `carry-infra-persistence/.../persistence/CollectionMapping.kt` (replaceAllFrom)
- 생성: `carry-user/.../domain/vo/Recipient.kt`
- 생성: `carry-notification/.../domain/vo/NotificationMessage.kt`, `NotificationReference.kt`
- 수정(도메인): `ShippingAddress.kt`, `Notification.kt`
- 수정(엔티티 toDomain만): `ShippingAddressJpaEntity.kt`, `NotificationJpaEntity.kt`
- 수정(엔티티 컬렉션): `ReviewJpaEntity.kt`, `LaundromatJpaEntity.kt`, `ServiceAreaJpaEntity.kt`, `PricePolicyJpaEntity.kt`, `DeliveryStepJpaEntity.kt`(+`DeliveryJpaEntity.kt` 주석)
- 수정(호출부): `NotificationCommandService.kt`, 각 모듈 테스트, `carry-app` ContractTest
- 생성(테스트): VO·헬퍼 단위 테스트

---

## Chunk 1: 컬렉션 통일 헬퍼 (Part B)

독립적·기계적·최저 리스크 → 먼저 수행해 파이프라인 검증. 각 치환은 **동작 불변**이어야 한다(clear 후 transform 결과 추가 = 기존과 동일).

### Task 1: `replaceAllFrom` 확장함수 + 단위 테스트

**Files:**
- Create: `carry-infra-persistence/src/main/kotlin/com/carry/infra/persistence/CollectionMapping.kt`
- Test: `carry-infra-persistence/src/test/kotlin/com/carry/infra/persistence/CollectionMappingTest.kt`

- [ ] **Step 1: 실패 테스트 작성** (기존 모듈 테스트 관용구 확인 후 assertion 스타일 맞출 것)

```kotlin
package com.carry.infra.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionMappingTest {
    @Test
    fun `replaceAllFrom replaces existing list elements via transform`() {
        val target = mutableListOf("old1", "old2")
        target.replaceAllFrom(listOf(1, 2, 3)) { "n$it" }
        assertEquals(listOf("n1", "n2", "n3"), target)
    }

    @Test
    fun `replaceAllFrom with empty source clears the collection`() {
        val target = mutableListOf("a", "b")
        target.replaceAllFrom(emptyList<Int>()) { "n$it" }
        assertTrue(target.isEmpty())
    }

    @Test
    fun `replaceAllFrom works on a MutableSet`() {
        val target = mutableSetOf("old")
        target.replaceAllFrom(listOf(1, 2)) { "n$it" }
        assertEquals(setOf("n1", "n2"), target)
    }
}
```

> **중요:** `carry-infra-persistence`는 **AssertJ 의존성이 없다**(루트 subprojects가 JUnit5만 전 모듈 제공, AssertJ는 모듈별 선언). 따라서 위처럼 **순수 JUnit5 단언**(`org.junit.jupiter.api.Assertions.*`)을 사용한다 — AssertJ import 금지. 이 모듈은 `src/test` 디렉터리가 아직 없을 수 있으니 새로 생성한다(build.gradle.kts 추가 의존성 불요).

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :carry-infra-persistence:test --tests "*CollectionMappingTest*"`
Expected: 컴파일 실패(`replaceAllFrom` 미정의).

- [ ] **Step 3: 최소 구현**

```kotlin
package com.carry.infra.persistence

/**
 * 자식 컬렉션을 도메인 소스로 "전체 교체"한다(clear 후 transform 결과 추가).
 * orphanRemoval=true 자식의 기존 관용구(clear()+add/addAll)와 동작이 동일하다.
 * MutableCollection 수신자라 List·Set 모두 적용 가능.
 *
 * 식별자 보존이 필요한 경우(예: Delivery.steps의 stepType 기준 in-place 갱신)에는
 * 사용하지 말 것 — 이 함수는 자식 행을 삭제 후 재삽입한다.
 */
fun <E, S> MutableCollection<E>.replaceAllFrom(source: Iterable<S>, transform: (S) -> E) {
    clear()
    source.forEach { add(transform(it)) }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :carry-infra-persistence:test --tests "*CollectionMappingTest*"`
Expected: PASS 3건. `build/test-results/test/*CollectionMappingTest*.xml`에서 tests=3 failures=0 확인.

- [ ] **Step 5: 커밋**

```
git add carry-infra-persistence/src/main/kotlin/com/carry/infra/persistence/CollectionMapping.kt \
        carry-infra-persistence/src/test/kotlin/com/carry/infra/persistence/CollectionMappingTest.kt
git commit -F <msg>   # "feat(infra): 컬렉션 전체 교체 헬퍼 replaceAllFrom 추가"
```

### Task 2: ReviewJpaEntity 적용

**Files:** Modify `carry-review/.../adapter/outbound/persistence/entity/ReviewJpaEntity.kt:53`

- [ ] **Step 1:** 파일을 읽어 현재 `updateFrom`의 `mediaList.clear()` + `forEach { mediaList.add(ReviewMediaJpaEntity(review = this, ...)) }` 블록을 확인.
- [ ] **Step 2:** 해당 블록을 다음으로 치환(transform이 `this` 캡처):

```kotlin
mediaList.replaceAllFrom(review.mediaUrls) { url ->
    ReviewMediaJpaEntity(review = this, mediaUrl = url)   // 실제 생성자 인자명: mediaUrl
}
```
import 추가: `import com.carry.infra.persistence.replaceAllFrom`
- [ ] **Step 3:** `./gradlew :carry-review:test` 실행, 기존 테스트 GREEN 유지 확인(XML tests/failures).
- [ ] **Step 4:** 커밋 `refactor(review): updateFrom mediaList 교체를 replaceAllFrom으로 통일`

### Task 3: LaundromatJpaEntity 적용 (options=MutableSet, mediaResources=MutableList)

**Files:** Modify `carry-laundromat/.../persistence/entity/LaundromatJpaEntity.kt:72`

- [ ] **Step 1:** 현재 `options.clear(); options.addAll(...)` 와 `mediaResources.clear(); mediaResources.addAll(...)` 확인.
- [ ] **Step 2:** 각각 `replaceAllFrom`으로 치환. `options`는 MutableSet이므로 `MutableCollection` 수신자로 정상 동작:

```kotlin
options.replaceAllFrom(laundromat.options) { it }        // 도메인 옵션이 그대로 저장형이면 it, 아니면 변환
mediaResources.replaceAllFrom(laundromat.mediaResources) { it /* 또는 매핑 */ }
```
> 현재 코드의 transform(매핑 람다)을 그대로 옮길 것. addAll(source) 형태면 transform은 `{ it }`, addAll(source.map{...})면 그 map 본문을 transform으로.
- [ ] **Step 3:** `./gradlew :carry-laundromat:test` GREEN 확인.
- [ ] **Step 4:** 커밋 `refactor(laundromat): updateFrom 컬렉션 교체를 replaceAllFrom으로 통일`

### Task 4: ServiceAreaJpaEntity 적용 (schedules, holidays)

**Files:** Modify `carry-service-availability/.../persistence/entity/ServiceAreaJpaEntity.kt:49`

- [ ] **Step 1:** `schedules.clear(); schedules.addAll(area.schedules.map{...})`, `holidays` 동일 확인.
- [ ] **Step 2:** 두 컬렉션 모두 `replaceAllFrom`으로 치환(map 본문을 transform으로 이동). import 추가.
- [ ] **Step 3:** `./gradlew :carry-service-availability:test` GREEN 확인.
- [ ] **Step 4:** 커밋 `refactor(service-availability): updateFrom 컬렉션 교체 통일`

### Task 5: PricePolicyJpaEntity 적용 (optionPrices)

**Files:** Modify `carry-price/.../persistence/entity/PricePolicyJpaEntity.kt:59`

- [ ] **Step 1:** `optionPrices.clear(); optionPrices.addAll(policy.optionPrices.map{...})` 확인.
- [ ] **Step 2:** `replaceAllFrom`으로 치환. import 추가.
- [ ] **Step 3:** `./gradlew :carry-price:test` GREEN 확인.
- [ ] **Step 4:** 커밋 `refactor(price): updateFrom optionPrices 교체 통일`

### Task 6: DeliveryStepJpaEntity 적용 + Delivery 제외 주석

**Files:**
- Modify `carry-delivery/.../persistence/entity/DeliveryStepJpaEntity.kt:52` (media 컬렉션)
- Modify `carry-delivery/.../persistence/entity/DeliveryJpaEntity.kt:65` (주석만)

- [ ] **Step 1:** `DeliveryStepJpaEntity.updateFrom`의 `media.clear()` + `forEach add` 블록을 `media.replaceAllFrom(step.mediaIds) { mediaId -> DeliveryStepMediaJpaEntity(deliveryStep = this, mediaId = mediaId) }`로 치환(실제 인자명: deliveryStep, mediaId). import 추가.
- [ ] **Step 2:** `DeliveryJpaEntity.updateFrom`의 steps 갱신부 위에 주석 추가:

```kotlin
// steps는 stepType 기준 in-place 갱신(식별자·@Version 보존)으로, "전체 교체"(replaceAllFrom)와
// 의미가 다르다. 자식 행을 삭제/재삽입하지 않으므로 의도적으로 다른 패턴을 유지한다.
```
- [ ] **Step 3:** `./gradlew :carry-delivery:test` GREEN 확인.
- [ ] **Step 4:** 커밋 `refactor(delivery): DeliveryStep media 교체 통일 + Delivery 식별자보존 주석`

### Chunk 1 검증 게이트
- [ ] `./gradlew :carry-infra-persistence:test :carry-review:test :carry-laundromat:test :carry-service-availability:test :carry-price:test :carry-delivery:test` 전부 GREEN(XML 확인).

---

## Chunk 2: ShippingAddress Recipient VO (Part A-2)

### Task 7: Recipient VO + 단위 테스트

**Files:**
- Create: `carry-user/src/main/kotlin/com/carry/user/domain/vo/Recipient.kt`
- Test: `carry-user/src/test/kotlin/com/carry/user/domain/vo/RecipientTest.kt`

- [ ] **Step 1: 실패 테스트** (기존 carry-user VO 테스트 스타일/임포트 확인 후 맞출 것 — 예: `Phone`/`Email` 테스트)

```kotlin
package com.carry.user.domain.vo

import com.carry.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecipientTest {
    @Test
    fun `creates recipient with name and phone`() {
        val r = Recipient("홍길동", "010-1234-5678")
        assertThat(r.name).isEqualTo("홍길동")
        assertThat(r.phone).isEqualTo("010-1234-5678")
    }

    @Test
    fun `rejects blank name`() {
        assertThatThrownBy { Recipient(" ", "010-1234-5678") }
            .isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `rejects blank phone`() {
        assertThatThrownBy { Recipient("홍길동", "") }
            .isInstanceOf(BusinessException::class.java)
    }
}
```
> `requireInput`는 `com.carry.common.exception.BusinessException`(ErrorCode.INVALID_INPUT)을 던진다 — 기존 `PhoneTest`와 동일. carry-user는 AssertJ 의존성이 있으므로 위 스타일 사용 가능.

- [ ] **Step 2:** Run `./gradlew :carry-user:test --tests "*RecipientTest*"` → 컴파일 실패 확인.
- [ ] **Step 3: 구현**

```kotlin
package com.carry.user.domain.vo

import com.carry.common.exception.requireInput

data class Recipient(
    val name: String,
    val phone: String,
) {
    init {
        requireInput(name.isNotBlank()) { "수령인 이름은 비어있을 수 없습니다" }
        requireInput(phone.isNotBlank()) { "수령인 전화번호는 비어있을 수 없습니다" }
    }
}
```
- [ ] **Step 4:** Run 동일 → PASS 3건(XML 확인).
- [ ] **Step 5:** 커밋 `feat(user): 수령인 Recipient VO 추가(검증 중앙화)`

### Task 8: ShippingAddress 도메인 리팩터 (Recipient 도입 + 위임 게터)

**Files:** Modify `carry-user/.../domain/model/ShippingAddress.kt`

- [ ] **Step 1:** 생성자 필드 `private var _recipientName`/`_recipientPhone` → `private var _recipient: Recipient`.
- [ ] **Step 2:** 위임 게터로 하위호환 유지:

```kotlin
val recipient: Recipient get() = _recipient
val recipientName: String get() = _recipient.name
val recipientPhone: String get() = _recipient.phone
```
- [ ] **Step 3:** `create`/`update`/`reconstitute` 시그니처에서 `recipientName,recipientPhone` 두 파라미터 → `recipient: Recipient` 하나. 본문에서 `_recipient = recipient` 대입. **`create`/`update`의 기존 `recipientName.isNotBlank()` require 2줄 제거**(Recipient.init로 이전됨). `alias`/`areaCode` 검증은 유지.
- [ ] **Step 4:** `./gradlew :carry-user:compileKotlin` 또는 `:carry-user:test`로 컴파일 확인 — 이 시점 호출부(엔티티 toDomain·서비스·테스트)가 깨질 것. 다음 태스크에서 수정.

### Task 9: ShippingAddressJpaEntity.toDomain 수정

**Files:** Modify `carry-user/.../persistence/entity/ShippingAddressJpaEntity.kt:51`

- [ ] **Step 1:** `toDomain()`의 `reconstitute(...)` 호출에서 `recipientName = recipientName, recipientPhone = recipientPhone,` → `recipient = Recipient(recipientName, recipientPhone),`. import 추가 `com.carry.user.domain.vo.Recipient`.
- [ ] **Step 2:** `updateFrom`·`fromDomain`은 **무변경**(위임 게터 `address.recipientName`/`recipientPhone` 그대로 동작) — 변경하지 말 것.
- [ ] **Step 3:** 컴파일 확인.

### Task 10: 호출부 갱신 (서비스 + 테스트 + carry-app ContractTest)

**Files (읽고 각각 수정):**
- `carry-user/.../application/service/ShippingAddressService.kt` — `create`/`update` 호출
- `carry-user/src/test/.../domain/model/ShippingAddressTest.kt` — create/update/reconstitute
- `carry-user/src/test/.../application/service/ShippingAddressServiceTest.kt` — reconstitute/생성
- `carry-app/src/test/kotlin/com/carry/app/contract/UserQueryPortAdapterContractTest.kt:24` — reconstitute

- [ ] **Step 1:** 각 호출부에서 `recipientName = x, recipientPhone = y` 인자를 `recipient = Recipient(x, y)`로 교체. import 추가.
  - 서비스가 인바운드 command/DTO의 평면 필드에서 만들면 `Recipient(command.recipientName, command.recipientPhone)`로 조립. (DTO/command 자체는 변경하지 않음 — 경계는 평면 유지.)
- [ ] **Step 2:** `./gradlew :carry-user:test` GREEN(XML: 기존 테스트 수 유지 + RecipientTest 3).
- [ ] **Step 3:** `./gradlew :carry-app:test --tests "*UserQueryPortAdapterContractTest*"` GREEN.
- [ ] **Step 4:** 커밋 `refactor(user): ShippingAddress 수령인을 Recipient VO로 묶고 호출부 갱신`

### Chunk 2 검증 게이트
- [ ] `./gradlew :carry-user:test` 전체 GREEN(XML).

---

## Chunk 3: Notification 메시지/참조 VO (Part A-1)

### Task 11: NotificationMessage + NotificationReference VO + 단위 테스트

**Files:**
- Create: `carry-notification/.../domain/vo/NotificationMessage.kt`
- Create: `carry-notification/.../domain/vo/NotificationReference.kt`
- Test: `carry-notification/src/test/.../domain/vo/NotificationVoTest.kt`

- [ ] **Step 1: 실패 테스트** (기존 carry-notification 테스트 스타일 확인)

```kotlin
package com.carry.notification.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationVoTest {
    @Test
    fun `message holds title and content`() {
        val m = NotificationMessage("제목", "본문")
        assertThat(m.title).isEqualTo("제목")
        assertThat(m.content).isEqualTo("본문")
    }

    @Test
    fun `reference holds type and id together`() {
        val r = NotificationReference("ORDER", 42L)
        assertThat(r.type).isEqualTo("ORDER")
        assertThat(r.id).isEqualTo(42L)
    }
}
```
> 두 VO는 불변 데이터 묶음이므로 검증 로직 없음(원시값 그대로). title/content blank 허용 여부는 현재 도메인이 검증 안 하므로 추가하지 않음(YAGNI·행동 보존).

- [ ] **Step 2:** Run → 컴파일 실패.
- [ ] **Step 3: 구현**

```kotlin
// NotificationMessage.kt
package com.carry.notification.domain.vo
data class NotificationMessage(val title: String, val content: String)
```
```kotlin
// NotificationReference.kt
package com.carry.notification.domain.vo
data class NotificationReference(val type: String, val id: Long)
```
- [ ] **Step 4:** Run → PASS 2건(XML).
- [ ] **Step 5:** 커밋 `feat(notification): 메시지/참조 VO 추가`

### Task 12: Notification 도메인 리팩터 (VO 도입 + 위임 게터)

**Files:** Modify `carry-notification/.../domain/model/Notification.kt`

- [ ] **Step 1:** 주 생성자: `title`/`content` → `message: NotificationMessage`,
  `referenceType: String?`/`referenceId: Long?` → `reference: NotificationReference?`.
- [ ] **Step 2:** 위임 게터 추가(하위호환 — DTO·엔티티 fromDomain이 이 게터들을 사용):

```kotlin
val title: String get() = message.title
val content: String get() = message.content
val referenceType: String? get() = reference?.type
val referenceId: Long? get() = reference?.id
```
- [ ] **Step 3:** `create`/`reconstitute` 시그니처에서 평면 4개 → `message`, `reference` 2개로 교체, 본문 대입 수정.
- [ ] **Step 4:** 컴파일 — 호출부 깨짐(다음 태스크에서 수정).

### Task 13: NotificationJpaEntity.toDomain 수정

**Files:** Modify `carry-notification/.../persistence/entity/NotificationJpaEntity.kt:53`

- [ ] **Step 1:** `toDomain()`의 reconstitute 호출:
  - `title = title, content = content,` → `message = NotificationMessage(title, content),`
  - `referenceType = referenceType, referenceId = referenceId,` → `reference = referenceType?.let { NotificationReference(it, referenceId!!) },`
  - import 추가(두 VO).
- [ ] **Step 2:** `fromDomain`·`updateFrom` **무변경**(위임 게터 사용) — 건드리지 말 것.
- [ ] **Step 3:** 컴파일 확인.

### Task 14: 호출부 갱신 (CommandService + 테스트, WebDto 검증)

**Files:**
- `carry-notification/.../application/service/NotificationCommandService.kt:24` — `Notification.create`
- `carry-notification/src/test/.../domain/model/NotificationTest.kt`
- `carry-notification/src/test/.../application/service/NotificationCommandServiceTest.kt` — reconstitute 3곳
- 검증만(변경 불필요 예상): `carry-notification/.../adapter/inbound/rest/dto/NotificationWebDto.kt`

- [ ] **Step 1:** `NotificationCommandService.send`의 `create(...)`에서
  `title = command.title, content = command.content,` → `message = NotificationMessage(command.title, command.content),`
  `referenceType = command.referenceType, referenceId = command.referenceId,` → `reference = command.referenceType?.let { NotificationReference(it, command.referenceId!!) },`
  import 추가. (이후 `notificationSenderPort.send(... title = command.title ...)`는 command 평면 필드 그대로 사용 — 변경 없음.)
- [ ] **Step 2:** 테스트들의 `create`/`reconstitute` 호출을 VO 인자로 갱신.
- [ ] **Step 3:** `NotificationWebDto`가 `notification.referenceType`/`referenceId`를 읽는데 위임 게터로 보존되는지 컴파일로 확인(코드 변경 불필요해야 정상). 만약 깨지면 위임 게터 누락 — Task 12로 돌아가 보강.
- [ ] **Step 4:** `./gradlew :carry-notification:test` GREEN(XML: 기존 + 신규 2).
- [ ] **Step 5:** 커밋 `refactor(notification): 메시지/참조를 VO로 묶고 호출부 갱신`

### Chunk 3 검증 게이트
- [ ] `./gradlew :carry-notification:test` 전체 GREEN(XML).

---

## Chunk 4: 전체 검증 + 마무리

### Task 15: 전체 빌드/테스트 + 라운드트립 점검

- [ ] **Step 1:** 영향 모듈 + carry-app 전체:
  `./gradlew :carry-notification:test :carry-user:test :carry-review:test :carry-laundromat:test :carry-service-availability:test :carry-price:test :carry-delivery:test :carry-infra-persistence:test :carry-app:test`
- [ ] **Step 2:** 각 모듈 `build/test-results/**/*.xml`에서 tests>0, failures=0, errors=0 **수치 확인**(BUILD SUCCESSFUL 신뢰 금지).
- [ ] **Step 3:** 엔티티 매핑 라운드트립 안전망 점검: 대상 엔티티(특히 Notification·ShippingAddress)에 `fromDomain(d).toDomain()` 동치 또는 영속성 통합 테스트가 이미 GREEN인지 확인. 없고 커버리지 공백이면 간단한 라운드트립 단위 테스트 1건 보강(VO 조립/분해 누락 검출용).
- [ ] **Step 4:** `git status`로 의도치 않은 변경(특히 `gradle.properties`·`ROADMAP.md`) 미스테이징 확인.

### Task 16: PR 생성 + 자율 머지

- [ ] **Step 1:** requesting-code-review 스킬로 자체 리뷰(선택) 후, `git push -u origin refactor/reconstitute-vo`.
- [ ] **Step 2:** `gh pr create --base develop` — 본문 한국어, 스펙/플랜 링크, 변경 요약(VO 2종 모듈 + 컬렉션 헬퍼 + 제외 근거).
- [ ] **Step 3:** CI GREEN 확인 후 `gh pr merge <n> --merge --delete-branch`(auto-merge 비활성이라 직접). 머지 후 `git checkout develop && git pull && git branch -f develop origin/develop`.
- [ ] **Step 4:** 메모리 갱신([[carry-platform-roadmap-progress]]·[[carry-platform-known-debts]])에 6.4 항목 해소 기록.

---

## 적용 순서 근거
Chunk 1(컬렉션)은 VO와 독립·기계적이라 먼저. Chunk 2·3는 서로 독립이나 동일 패턴(위임 게터)이라 2를 먼저 해 패턴을 확립한 뒤 3 반복. 위임 게터 덕에 엔티티 `fromDomain`/`updateFrom`·REST DTO는 무변경, `toDomain`과 팩토리 호출부만 수정 — 리플 최소.
