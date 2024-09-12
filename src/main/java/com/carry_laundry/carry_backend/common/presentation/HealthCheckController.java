package com.carry_laundry.carry_backend.common.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/health")
public class HealthCheckController {

    @GetMapping
    public Mono<Void> healthCheck() {
        return Mono.empty();
    }
}
