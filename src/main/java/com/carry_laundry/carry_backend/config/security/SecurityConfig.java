package com.carry_laundry.carry_backend.config.security;

import com.carry_laundry.carry_backend.config.security.jwt.JWTAuthenticationFilter;
import com.carry_laundry.carry_backend.config.security.jwt.JWTHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTHelper jwtHelper;

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/api-docs/**",
                    "/webjars/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/api/v1/laundromats/**",
                    "/api/v1/laundromats/**",
                    "/api/v1/sign/**",
                    "/api/v1/addresses/**",
                    "/api/v1/service-availability/**",
                    "/api/v1/prices/**",
                    "/api/v1/media/**").permitAll()
                .anyExchange().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                .authenticationEntryPoint((exchange, ex) -> Mono.fromRunnable(() -> {
                    log.warn("UNAUTHORIZED");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                }))
                .accessDeniedHandler((exchange, denied) -> Mono.fromRunnable(() -> {
                    log.warn("FORBIDDEN");
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                })))
            .build();
    }

    JWTAuthenticationFilter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter(jwtHelper);
    }
}
