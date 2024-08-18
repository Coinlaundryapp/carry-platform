package org.example.coin_laundry_app_backend.config.security;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTAuthenticationFilter;
import org.example.coin_laundry_app_backend.config.security.jwt.JWTHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTHelper jwtHelper;

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity httpSecurity) {
        return httpSecurity
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .authorizeExchange(
                exchanges -> {
                    exchanges.pathMatchers("/api/v1/sign/**").permitAll();
                    exchanges.pathMatchers("/api-docs/**").permitAll();
                    exchanges.pathMatchers("/webjars/swagger-ui/**").permitAll();
                    exchanges.pathMatchers("/v3/api-docs/**").permitAll();
                    exchanges.anyExchange().authenticated();
                }
            )
            .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    @Bean
    JWTAuthenticationFilter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter(jwtHelper);
    }
}
