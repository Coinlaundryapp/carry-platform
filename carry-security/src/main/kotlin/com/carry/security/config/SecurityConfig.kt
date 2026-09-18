package com.carry.security.config

import com.carry.security.filter.JwtAuthenticationFilter
import com.carry.security.handler.CarryAccessDeniedHandler
import com.carry.security.handler.CarryAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.Customizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authenticationEntryPoint: CarryAuthenticationEntryPoint,
    private val accessDeniedHandler: CarryAccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            // 프론트(customer/carrier/coordinator-web)가 브라우저에서 직접 호출하므로 CORS 허용.
            // corsConfigurationSource 빈을 자동 사용한다(아래 빈 참조).
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v2/auth/**",
                        "/actuator/**",
                        "/swagger-ui/**",
                        // springdoc.api-docs.path=/api-docs 로 커스터마이즈돼 있어 기본 `/v3/api-docs/**`만으로는
                        // OpenAPI 스펙(JSON)·swagger-config가 401이 된다(Swagger UI 스펙 로딩·스키마 export 차단).
                        "/api-docs/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    /**
     * 프론트 브라우저 클라이언트용 CORS. 로컬 dev/e2e는 여러 프론트 포트(customer 3000·carrier 3001·
     * coordinator 3002 등)를 쓰므로 포트 와일드카드(Spring 문법 ":[*]")로 localhost를 한 번에 허용한다.
     * allowCredentials=true라 "*"(allowedOrigins) 대신 allowedOriginPatterns를 써야 한다.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("http://localhost:[*]", "https://www.carrylaundry.com")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 86_400L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
