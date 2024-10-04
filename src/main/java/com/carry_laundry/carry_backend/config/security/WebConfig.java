package com.carry_laundry.carry_backend.config.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000", "https://www.carrylaundry.com")
                .allowedMethods("PUT", "DELETE", "GET", "POST", "PATCH", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }
}