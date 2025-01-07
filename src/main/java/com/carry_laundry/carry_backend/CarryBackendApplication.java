package com.carry_laundry.carry_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {ReactiveUserDetailsServiceAutoConfiguration.class})
public class CarryBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarryBackendApplication.class, args);
    }
}