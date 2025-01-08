package com.carry_laundry.carry_backend.user.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.application.record.availability.AvailableRegion;
import com.carry_laundry.carry_backend.user.application.record.availability.InspectionResult;
import com.carry_laundry.carry_backend.user.application.service.ServiceAvailabilityService;
import com.carry_laundry.carry_backend.user.domain.entity.AvailabilityNotification;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.CreateNotificationRequest;
import com.carry_laundry.carry_backend.user.presentation.payload.request.availability.QueryAvailabilityRequest;
import java.util.List;
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

    private final ServiceAvailabilityService serviceAvailabilityService;

    @GetMapping("/regions")
    public Mono<ApiCommonResponse<List<AvailableRegion>>> getAvailableRegions() {
        return serviceAvailabilityService.getAvailableRegions()
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping
    public Mono<ApiCommonResponse<InspectionResult>> queryAvailability(
        QueryAvailabilityRequest request) {
        return serviceAvailabilityService.query(request)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/notifications")
    public Mono<ApiCommonResponse<AvailabilityNotification>> registerNotification(
        @RequestBody CreateNotificationRequest request) {
        return serviceAvailabilityService.register(request)
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
