# userId MDC 채우기 — 설계

> 작성일: 2026-06-10 · ROADMAP 3.3(구조화 로깅 강화) 후속
> 선행: Phase 4 인증(JwtAuthenticationFilter)·구조화 로깅 기반 완료

## 1. 배경 / 문제

`logback-spring.xml`은 JSON appender에 `<includeMdcKeyName>userId</includeMdcKeyName>`를 선언해
로그에 `userId` MDC 값을 싣도록 준비돼 있으나, **MDC에 `userId`를 채워 넣는 주체가 없다.**
결과적으로 모든 로그의 `userId`는 항상 비어 있어, 사용자 단위 로그 상관관계가 불가능하다.

`traceId`/`spanId`는 Micrometer Tracing이, `saga.traceId`는 `EventConsumerSupport`가,
`orderId`는 `SagaLogContext`가 각각 채운다. **인증된 사용자 식별자(`userId`)만 빠져 있다.**

## 2. 목표 / 비목표

**목표**
- 인증된 요청의 처리 동안 MDC에 `userId`가 채워져, 그 요청에서 발생하는 모든 로그가
  사용자 단위로 상관관계를 가진다.
- 요청 종료 시 MDC를 정리해 스레드풀 재사용 시 다음 요청으로 값이 새지 않는다.

**비목표**
- 인증/인가 동작 변경 (X — 로그 MDC 필드만 추가, SecurityContext 세팅 로직 불변).
- 비인증 요청에 가짜 userId 부여 (X — 인증 성공 시에만 채움).
- 새 MDC 유틸 추상화 도입 (X — 요청 진입 필터라 중첩 없음, 표준 put/remove로 충분).

## 3. 설계

### 3.1 `JwtAuthenticationFilter` — 인증 성공 시 MDC 채움 + 종료 시 정리

`doFilterInternal`에서:
- 토큰이 유효해 `principal`을 얻고 `SecurityContext`에 authentication을 세팅한 **직후**
  `MDC.put("userId", principal.userId.toString())`.
- `filterChain.doFilter(...)`를 `try`로 감싸고 `finally`에서 `MDC.remove("userId")`.
  - 인증 실패/무토큰이면 put을 안 하지만, finally의 remove는 미설정 키에도 no-op이라 안전하고,
    **스레드 재사용으로 남은 이전 요청 잔재까지 청소**한다.

```kotlin
override fun doFilterInternal(request, response, filterChain) {
    val token = resolveToken(request)
    if (token != null) {
        val principal = jwtProvider.parseToken(token)
        if (principal != null) {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(principal.userId, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role}")))
            MDC.put(MDC_USER_ID, principal.userId.toString())   // ★ 반드시 if(principal != null) 내부 — 비인증 요청에 "null" 넣지 않도록
        }
    }
    try {
        filterChain.doFilter(request, response)
    } finally {
        MDC.remove(MDC_USER_ID)
    }
}
// companion: private const val MDC_USER_ID = "userId"  (logback includeMdcKeyName과 일치)
```

**정리 방식 — 단순 remove (save/restore 아님).** `SagaLogContext.withOrderId`는 중첩 호출 안전성을
위해 이전 값을 보존·복구하지만, 이 필터는 **HTTP 요청 진입점**이라 진입 시 동일 스레드에
유효한 `userId`가 있을 수 없다(있다면 이전 요청의 누수이므로 오히려 지워야 함). 따라서 표준
servlet 필터 MDC 패턴인 put-그리고-finally-remove로 충분하다.

### 3.2 `logback-spring.xml` — 콘솔 패턴에 userId 노출 (일관성)

콘솔 패턴이 `order=%mdc{orderId:-}`까지만 보여주고 userId는 JSON appender에만 있다.
일관성을 위해 콘솔 패턴 끝에 `,user=%mdc{userId:-}`를 추가한다(JSON appender는 이미 포함, 무변경).

## 4. 테스트 (`JwtAuthenticationFilterTest` 확장)

MDC는 thread-local이라, 필터 체인 **실행 중**의 MDC 상태를 캡처해야 한다. mock `FilterChain`의
`doFilter` 콜백에서 `MDC.get("userId")`를 기록한 뒤, 필터 종료 후 값을 단언한다.

- **유효 토큰**: 체인 실행 중 `MDC.get("userId") == principal.userId.toString()`; 필터 종료 후 `null`.
- **무토큰**: 체인 실행 중·종료 후 모두 `userId` 없음.
- **무효 토큰**(`parseToken` → null): 체인 실행 중·종료 후 모두 `userId` 없음.
- **체인이 예외를 던짐**: 필터 종료(예외 전파) 후에도 `userId` 정리됨(`finally` 보장).
- 각 테스트는 누수 방지를 위해 시작 시 `MDC.clear()`(또는 `@BeforeEach`).

## 5. 검증

- `:carry-security:test` GREEN — JUnit XML로 `JwtAuthenticationFilterTest` 카운트 확인.
- 기존 필터 동작(SecurityContext 세팅·role 권한) 회귀 없음.

## 6. 영향 범위

| 파일 | 변경 |
|---|---|
| `carry-security/.../filter/JwtAuthenticationFilter.kt` | MDC put(인증 성공 시) + try/finally remove |
| `carry-security/.../filter/JwtAuthenticationFilterTest.kt` | MDC 채움/정리 테스트 4건 추가 |
| `carry-app/src/main/resources/logback-spring.xml` | 콘솔 패턴에 `user=%mdc{userId:-}` 추가 |
| 인증/인가 로직 | 무변경 |
