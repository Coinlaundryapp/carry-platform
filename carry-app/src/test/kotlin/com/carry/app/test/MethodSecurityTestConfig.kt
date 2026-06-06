package com.carry.app.test

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * @WebMvcTest 슬라이스는 SecurityConfig 를 제외하므로 메서드 시큐리티가 비활성이다.
 * 이 설정을 @Import 하면 슬라이스에서도 @PreAuthorize 가 실제로 동작해
 * 가드 테스트(403)가 거짓 통과하지 않는다.
 */
@TestConfiguration
@EnableMethodSecurity
class MethodSecurityTestConfig
