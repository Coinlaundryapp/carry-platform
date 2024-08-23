package org.example.coin_laundry_app_backend.config.security;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTAuthenticationFilter;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

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
                        .pathMatchers("/api-docs/**").permitAll()
                        .pathMatchers("/webjars/swagger-ui/**").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        .pathMatchers("/health").permitAll()
                        .pathMatchers("/api/v1/sign/**").permitAll()
                        .pathMatchers("/api/v1/addresses/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                        .authenticationEntryPoint((exchange, ex) -> Mono.fromRunnable(() -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        }))
                        .accessDeniedHandler((exchange, denied) -> Mono.fromRunnable(() -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        })))
                .build();
    }

    JWTAuthenticationFilter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter(jwtHelper);
    }
}
