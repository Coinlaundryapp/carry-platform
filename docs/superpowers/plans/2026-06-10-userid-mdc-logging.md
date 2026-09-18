# userId MDC 채우기 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증된 요청 처리 동안 `userId`를 MDC에 채워, 그 요청의 모든 로그가 사용자 단위로 상관관계를 갖게 하고, 요청 종료 시 정리해 스레드풀 재사용 누수를 막는다.

**Architecture:** `JwtAuthenticationFilter`(carry-security)가 인증 성공(`principal != null`) 시 `MDC.put("userId", principal.userId.toString())`를 호출하고, `filterChain.doFilter(...)`를 `try`로 감싸 `finally`에서 `MDC.remove("userId")`로 정리한다. 인증/인가 로직은 불변. logback 콘솔 패턴에 `user=%mdc{userId:-}`를 추가해 콘솔에도 노출(JSON appender는 이미 포함).

**Tech Stack:** Kotlin, Spring Security(`OncePerRequestFilter`), SLF4J `MDC`, JUnit5, mockk, AssertJ, spring-test mock servlet, Gradle(JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-10-userid-mdc-logging-design.md`

---

## 사전 환경 메모 (실행자 필독)

- **JDK 21 필수.** `carry-platform/gradle.properties`에 `org.gradle.java.home=C:/Users/Eisen/.jdks/ms-21.0.7`가 있는지 확인(미커밋, 스테이징 금지). 루트 `ROADMAP.md`(??)도 스테이징 금지.
- **테스트 검증은 JUnit XML로.** `carry-security/build/test-results/test/TEST-com.carry.security.filter.JwtAuthenticationFilterTest.xml`의 `tests`/`failures`/`errors` 카운트와 `<testcase>` 노드 확인. `BUILD SUCCESSFUL`만 믿지 말 것.
- 종료코드는 `; echo "EXIT=$?"`(파이프 금지).
- 커밋: 한국어 본문 + 영어 conventional prefix + `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`, `git commit -F - <<'EOF' ... EOF`(heredoc). Bash 도구 사용.

---

## File Structure

| 파일 | 책임 | 변경 |
|---|---|---|
| `carry-security/src/main/kotlin/com/carry/security/filter/JwtAuthenticationFilter.kt` | JWT 인증 + (신규) 요청 단위 userId MDC | put(인증 성공 시) + try/finally remove |
| `carry-security/src/test/kotlin/com/carry/security/filter/JwtAuthenticationFilterTest.kt` | 필터 단위 테스트 | MDC 채움/정리 테스트 4건 추가, MDC 정리 teardown |
| `carry-app/src/main/resources/logback-spring.xml` | 로그 포맷 | 콘솔 패턴에 `,user=%mdc{userId:-}` 추가 |

**기존 코드 (변경 전):**

```kotlin
// JwtAuthenticationFilter.doFilterInternal (현재)
override fun doFilterInternal(request, response, filterChain) {
    val token = resolveToken(request)
    if (token != null) {
        val principal = jwtProvider.parseToken(token)
        if (principal != null) {
            val authentication = UsernamePasswordAuthenticationToken(
                principal.userId, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role}"))
            )
            SecurityContextHolder.getContext().authentication = authentication
        }
    }
    filterChain.doFilter(request, response)
}
```

`JwtPrincipal(userId: Long, role: String)` — `userId`는 Long. 기존 테스트는 `MockFilterChain` 사용, teardown은 `SecurityContextHolder.clearContext()`만.

logback 콘솔 패턴(현재, `carry-app/src/main/resources/logback-spring.xml`):
```
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [trace=%mdc{traceId:-},span=%mdc{spanId:-},saga=%mdc{saga.traceId:-},order=%mdc{orderId:-}] - %msg%n</pattern>
```

---

## Chunk 1: userId MDC 채움 + 정리

### Task 1: 인증 시 MDC 채움 테스트 (RED)

**Files:**
- Modify: `carry-security/src/test/kotlin/com/carry/security/filter/JwtAuthenticationFilterTest.kt`

MDC는 thread-local이라 **필터 체인 실행 중**의 값을 캡처해야 한다. `MockFilterChain`은 콜백을 실행하지 않으므로, `MDC.get`을 기록하는 **람다 기반 FilterChain**을 쓴다. 또 테스트 간 MDC 누수를 막기 위해 teardown에 `MDC.clear()`를 추가한다.

- [ ] **Step 1: import 추가 + teardown에 MDC.clear + 캡처 헬퍼 + 4개 테스트 추가**

파일 상단 import에 추가(ASCII 정렬: `org.slf4j.MDC`는 `org.junit...`과 `org.springframework...` 사이):
```kotlin
import jakarta.servlet.FilterChain
import org.slf4j.MDC
```

기존 `@AfterEach tearDown()`에 MDC 정리 추가:
```kotlin
    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }
```

클래스에 캡처 헬퍼와 4개 테스트 추가:
```kotlin
    /** filterChain 실행 "도중"의 userId MDC 값을 기록하는 FilterChain. */
    private class CapturingChain(val onInvoke: () -> Unit = {}) : FilterChain {
        var userIdDuringChain: String? = "__not_invoked__"
        override fun doFilter(req: jakarta.servlet.ServletRequest?, res: jakarta.servlet.ServletResponse?) {
            userIdDuringChain = MDC.get("userId")
            onInvoke()
        }
    }

    @Test
    fun `유효한 토큰이면 체인 실행 중 userId가 MDC에 있고 종료 후 정리된다`() {
        every { jwtProvider.parseToken("good-token") } returns JwtPrincipal(99L, "CUSTOMER")
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }
        val chain = CapturingChain()

        sut.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isEqualTo("99")
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `토큰이 없으면 체인 실행 중에도 종료 후에도 userId MDC가 없다`() {
        val chain = CapturingChain()

        sut.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isNull()
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `토큰 검증에 실패하면 체인 실행 중에도 종료 후에도 userId MDC가 없다`() {
        every { jwtProvider.parseToken("bad-token") } returns null
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer bad-token") }
        val chain = CapturingChain()

        sut.doFilter(request, MockHttpServletResponse(), chain)

        assertThat(chain.userIdDuringChain).isNull()
        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun `체인이 예외를 던져도 userId MDC는 정리된다`() {
        every { jwtProvider.parseToken("good-token") } returns JwtPrincipal(99L, "CUSTOMER")
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Bearer good-token") }
        val chain = CapturingChain(onInvoke = { throw RuntimeException("downstream") })

        assertThatThrownBy { sut.doFilter(request, MockHttpServletResponse(), chain) }
            .isInstanceOf(RuntimeException::class.java)

        assertThat(MDC.get("userId")).isNull()
    }
```

> `assertThatThrownBy` 사용을 위해 `import org.assertj.core.api.Assertions.assertThatThrownBy`를 추가한다(기존엔 `assertThat`만 import됨).

- [ ] **Step 2: RED 확인**

Run: `./gradlew :carry-security:test --tests "com.carry.security.filter.JwtAuthenticationFilterTest" ; echo "EXIT=$?"`
Expected: **실패**(`EXIT` ≠ 0). 프로덕션이 아직 MDC를 안 채우므로 `유효한 토큰...` 테스트의 `chain.userIdDuringChain == "99"` 단언이 `null`로 실패. (예외/무토큰/무효토큰 테스트는 이미 통과할 수 있음 — 핵심 RED는 유효토큰 케이스.) 커밋 없음.

---

### Task 2: 필터에 MDC 채움 + 정리 구현 (GREEN)

**Files:**
- Modify: `carry-security/src/main/kotlin/com/carry/security/filter/JwtAuthenticationFilter.kt`

- [ ] **Step 1: import + companion 상수 + doFilterInternal 수정**

`import org.slf4j.MDC` 추가(ASCII 정렬: `OncePerRequestFilter` import 위, `org.springframework...` 블록 다음의 적절한 위치 — 실제 정렬은 `org.slf4j`가 `org.springframework`보다 앞). `doFilterInternal` 본문을 아래로 교체:

```kotlin
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        if (token != null) {
            val principal = jwtProvider.parseToken(token)
            if (principal != null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    principal.userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_${principal.role}"))
                )
                SecurityContextHolder.getContext().authentication = authentication
                MDC.put(MDC_USER_ID, principal.userId.toString())
            }
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 인증 성공 시 채운 userId를 항상 정리(스레드풀 재사용 누수 방지). 미설정 키 remove는 no-op.
            MDC.remove(MDC_USER_ID)
        }
    }

    private companion object {
        const val MDC_USER_ID = "userId"  // logback includeMdcKeyName과 일치
    }
```

- [ ] **Step 2: GREEN 확인**

Run: `./gradlew :carry-security:test --tests "com.carry.security.filter.JwtAuthenticationFilterTest" ; echo "EXIT=$?"`
Expected: `EXIT=0`. XML `TEST-com.carry.security.filter.JwtAuthenticationFilterTest.xml`에 기존 3 + 신규 4 = **`tests="7" failures="0" errors="0"`**.

- [ ] **Step 3: 모듈 회귀 확인**

Run: `./gradlew :carry-security:test ; echo "EXIT=$?"`
Expected: `EXIT=0`, carry-security 전체 GREEN.

- [ ] **Step 4: 커밋 (테스트 + 프로덕션 함께)**

```bash
git add carry-security/src/main/kotlin/com/carry/security/filter/JwtAuthenticationFilter.kt \
        carry-security/src/test/kotlin/com/carry/security/filter/JwtAuthenticationFilterTest.kt
git commit -F - <<'EOF'
feat(logging): JwtAuthenticationFilter가 인증 성공 시 userId를 MDC에 채움

logback이 기대하던 userId MDC를 인증 성공 시 채우고, filterChain을 try/finally로
감싸 요청 종료 시 정리(스레드풀 재사용 누수 방지). 인증/인가 동작 변경 없음.
필터 테스트 4건 추가(체인 실행 중 채움·종료 후 정리·무토큰·예외 시 정리).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: logback 콘솔 패턴에 userId 노출

**Files:**
- Modify: `carry-app/src/main/resources/logback-spring.xml`

- [ ] **Step 1: 콘솔 패턴에 user 필드 추가**

콘솔 `<pattern>`의 `order=%mdc{orderId:-}` 바로 뒤(닫는 `]` 앞)에 `,user=%mdc{userId:-}`를 삽입:
```
... order=%mdc{orderId:-},user=%mdc{userId:-}] - %msg%n
```
(JSON appender의 `<includeMdcKeyName>userId</includeMdcKeyName>`는 이미 있으므로 무변경.)

- [ ] **Step 2: 컴파일/리소스 확인 (가벼운 빌드)**

Run: `./gradlew :carry-app:compileKotlin ; echo "EXIT=$?"`
Expected: `EXIT=0`. (logback은 런타임 설정이라 컴파일만 확인. 형식 오류 없으면 충분.)

- [ ] **Step 3: 커밋**

```bash
git add carry-app/src/main/resources/logback-spring.xml
git commit -F - <<'EOF'
chore(logging): 콘솔 로그 패턴에 user=%mdc{userId} 추가

order 필드와 일관되게 콘솔에도 userId 노출. JSON appender는 이미 userId 포함.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Chunk 2: 검증 + PR

### Task 4: 모듈 검증

**Files:** 없음

- [ ] **Step 1: carry-security 전체 테스트**

Run: `./gradlew :carry-security:test ; echo "EXIT=$?"`
Expected: `EXIT=0`. `JwtAuthenticationFilterTest` = 7, 모듈 전체 failures=0.

- [ ] **Step 2: 라이브 스모크 불요 판단**

순수 로깅 MDC 변경(인증/인가/스키마 동작 무변경). 필터 동작은 단위 테스트로 충분히 증명. 라이브 풀스택 스모크 생략 — PR 본문에 근거 명시.

---

### Task 5: PR + 자율 머지

**Files:** 없음

- [ ] **Step 1: develop 동기 확인**

Run: `git fetch origin -q ; git rev-list --left-right --count HEAD...origin/develop ; echo "EXIT=$?"`
Expected: 우측(develop only)=0이거나, 충돌 없으면 진행.

- [ ] **Step 2: 푸시 + PR(base develop)**

```bash
git push -u origin feature/userid-mdc-logging
gh pr create --base develop --title "feat(logging): userId MDC 채우기 — 인증 요청 로그 사용자 상관관계" --body-file .pr-body-tmp.md
```
PR 본문(`.pr-body-tmp.md`, 한국어): 문제(logback이 userId 기대하나 채우는 주체 없음) / 해결(필터 인증 성공 시 put + finally remove, 콘솔 패턴 노출) / 검증(carry-security 7 GREEN, 예외/누수 정리 테스트 포함) / 범위(인증·인가 무변경) / 라이브 스모크 생략 근거.

- [ ] **Step 3: 임시 파일 정리 (별도 호출)**

```bash
rm .pr-body-tmp.md
```

- [ ] **Step 4: 자율 머지 (직접 머지 — repo auto-merge 비활성)**

```bash
gh pr merge <PR번호> --merge --delete-branch
```

- [ ] **Step 5: 로컬 정리**

```bash
git checkout develop && git fetch --prune && git pull --ff-only origin develop
```

---

## 완료 기준

- [ ] `JwtAuthenticationFilter`가 인증 성공 시 `userId` MDC를 채우고 요청 종료 시(예외 포함) 정리.
- [ ] `JwtAuthenticationFilterTest` 7건(기존 3 + 신규 4) GREEN.
- [ ] logback 콘솔 패턴에 `user=%mdc{userId:-}` 노출.
- [ ] PR develop 머지 완료, 브랜치 삭제.
- [ ] 메모리 갱신: [[carry-platform-known-debts]]의 "userId MDC 채우기"를 ✅ 해소로 이동, [[carry-platform-roadmap-progress]] 반영.
