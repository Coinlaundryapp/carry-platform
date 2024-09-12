package com.carry_laundry.carry_backend.user.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.application.service.ServiceAvailabilityService;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.AvailabilityQueryRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/service-availability")
@RequiredArgsConstructor
public class ServiceAvailabilityController {

    final private ServiceAvailabilityService serviceAvailabilityService;

    @GetMapping
    public Mono<ApiCommonResponse<?>> queryAvailability(AvailabilityQueryRequest request) {
        return serviceAvailabilityService.query(request)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/notifications")
    public Mono<ApiCommonResponse<?>> registerNotification(
        @RequestBody CreateNotificationRequest request) {
        return serviceAvailabilityService.register(request)
            .then(Mono.empty())
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
