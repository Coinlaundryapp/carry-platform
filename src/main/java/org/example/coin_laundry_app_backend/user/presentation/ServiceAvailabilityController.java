package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.AddressSearchService;
import org.example.coin_laundry_app_backend.user.application.service.ServiceAvailabilityService;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.address.AddressSearchRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.AvailabilityQueryRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import org.springframework.web.bind.annotation.*;
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
    public Mono<ApiCommonResponse<?>> registerNotification(@RequestBody CreateNotificationRequest request) {
        return serviceAvailabilityService.register(request)
                .then(Mono.empty())
                .map(ApiCommonResponse::createSuccessResponse);
    }
}
