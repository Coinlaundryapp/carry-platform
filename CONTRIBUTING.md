# 기여 가이드

carry-platform 은 분산 시스템·헥사고날 아키텍처·사가 패턴을 시연하는 학습/포트폴리오 프로젝트다.
이 문서는 코드를 더할 때 지켜야 할 구조 규칙과 워크플로를 요약한다. "왜 이렇게 하는가"는
[`docs/adr/`](docs/adr/) 의 ADR들이 설명한다.

## 빠른 시작

```bash
# 1) 인프라 기동(postgres·redis·kafka 3-node·debezium·observability)
./scripts/dev-up.sh        # 또는 Windows: ./scripts/dev-up.ps1

# 2) 애플리케이션 실행 — 로컬에선 트레이싱 export 를 꺼야 요청이 블록되지 않는다
MANAGEMENT_TRACING_ENABLED=false ./gradlew :carry-app:bootRun

# 3) 빌드 & 테스트
./gradlew build
```

> JDK 21 필요. 시스템 기본 JDK 가 다르면 `gradle.properties` 에
> `org.gradle.java.home=<JDK21 경로>` 를 두자(머신 특정이므로 커밋하지 않는다).

## 아키텍처 규칙 (ArchUnit 으로 강제)

각 도메인 모듈은 **헥사고날 3계층**을 따른다. `HexagonalArchitectureTest`·`ModuleBoundaryTest`
가 빌드에서 다음을 강제하므로, 어기면 테스트가 깨진다.

- `domain` — 순수 Kotlin. `jakarta.persistence`·Spring 의존 금지(→ [ADR-0001](docs/adr/0001-domain-jpa-separation.md)).
- `application` — UseCase(`port/inbound`) + 아웃바운드 포트(`port/outbound`) + `service`. 어댑터 의존 금지.
- `adapter` — `inbound`(rest/kafka) + `outbound`(persistence/redis/...). 인바운드는 아웃바운드에 의존 금지.
- **도메인 모듈 간 직접 의존 금지.** 크로스모듈은 이벤트(사가) 또는 명시적 쿼리 포트로만, 결선은
  `carry-app` 에서(→ [ADR-0003](docs/adr/0003-module-decomposition-criteria.md)).

### 도메인 모델 ↔ JPA (3-Method Pattern)

도메인 모델은 `@Entity` 를 달지 않는다. JPA 엔티티가 매핑을 소유한다:

- `toDomain()` — 엔티티 → 도메인
- `fromDomain()` — 신규 도메인 → 엔티티
- `updateFrom()` — 기존 엔티티에 도메인 상태 동기화

도메인 생성은 `create()`(신규)/`reconstitute()`(영속 복원) 두 팩토리로 구분한다.

### 이벤트 / 사가

- 상태 변경 이벤트는 **Outbox** 에 같은 트랜잭션으로 기록하고 Debezium CDC 가 Kafka 로 흘린다.
  직접 Kafka produce 금지(dual-write 회피, → [ADR-0002](docs/adr/0002-outbox-cdc-over-dual-write.md)).
- 워크플로는 **Choreography Saga** — 각 모듈이 이벤트를 구독해 자기 상태만 전이(→ [ADR-0004](docs/adr/0004-choreography-saga.md)).
- 이벤트 스키마는 **가산적 호환**만(새 필드는 선택적). 호환 깸 = 새 `eventType`(→ [ADR-0005](docs/adr/0005-event-schema-evolution.md)).
- 소비자는 멱등(`processIfNotDuplicate`)을 전제로 작성한다.

### 새 모듈 만들기

```bash
./scripts/new-module.sh carry-foo com.carry.foo
```

헥사고날 골격(domain/application/adapter + build.gradle.kts)을 생성한다. 생성 후
`settings.gradle.kts` 에 `include("carry-foo")` 를 추가하고, 크로스모듈 결선이 필요하면
`carry-app` 에 어댑터를 둔다.

## 테스트 기준

- 단위 테스트는 mockk 로 협력자를 격리. **크로스모듈 계약/Fake 는 mockk 금지** — `testFixtures`
  의 `Fake<Port>` 사용(CDC 계약 패턴).
- 통합/사가 테스트는 `carry-app` 의 Testcontainers 기반.
- 검증은 `BUILD SUCCESSFUL` 만 믿지 말고 `build/test-results/**/*.xml` 의 tests/failures 로 확인.

## 커밋 / PR

- 브랜치: `git checkout -b <type>/<name> origin/develop` (PR base 는 항상 `develop`).
- Conventional Commit 접두사(feat/fix/refactor/docs/ci/perf/test/build/chore) + **한국어 본문**.
- PR 템플릿을 채우고, 변경의 *무엇/왜/검증* 을 적는다.
- 대응 이슈가 있으면 PR 본문에 **`Closes #N`** 을 포함한다 — 머지 시 이슈가 자동으로 닫힌다.
  (누락하면 완료된 이슈가 열린 채 남는다 — #74~#94 9건이 실제 사례.)
- 머지 전 영향 모듈 `:test` + `:carry-app:test`(Testcontainers) 통과 확인.
