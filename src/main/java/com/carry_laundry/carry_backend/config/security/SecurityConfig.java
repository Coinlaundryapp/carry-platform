package com.carry_laundry.carry_backend.config.security;

import com.carry_laundry.carry_backend.common.security.CustomAuthenticationEntryPoint;
import com.carry_laundry.carry_backend.common.security.filter.TermVerificationFilter;
import com.carry_laundry.carry_backend.common.security.filter.UserAuthenticationFilter;
import com.carry_laundry.carry_backend.config.security.jwt.JWTHelper;
import com.carry_laundry.carry_backend.config.security.jwt.JWTTokenParseFilter;
import com.carry_laundry.carry_backend.term.repository.TermInMemoryCache;
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
    private final TermInMemoryCache termInMemoryCache;

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
                    "/api/v1/media/*",
                    "/api/v1/reviews/laundromat/*").permitAll()
                .pathMatchers("/api/v1/terms/agreements/**").authenticated()
                .pathMatchers("/api/v1/terms/**").permitAll()
                .anyExchange().authenticated()
            )
            .addFilterBefore(jwtTokenParseFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterBefore(userAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterBefore(termVerificationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                .accessDeniedHandler((exchange, denied) -> Mono.fromRunnable(() -> {
                    log.warn("FORBIDDEN");
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                })))
            .build();
    }

    JWTTokenParseFilter jwtTokenParseFilter() {
        return new JWTTokenParseFilter(jwtHelper);
    }

    UserAuthenticationFilter userAuthenticationFilter() {
        return new UserAuthenticationFilter();
    }

    TermVerificationFilter termVerificationFilter() {
        return new TermVerificationFilter(termInMemoryCache);
    }
}
