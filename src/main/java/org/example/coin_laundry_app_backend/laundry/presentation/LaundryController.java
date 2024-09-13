package org.example.coin_laundry_app_backend.laundry.presentation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.laundry.application.LaundryService;
import org.example.coin_laundry_app_backend.laundry.presentation.api.LaundrySwagger;
import org.example.coin_laundry_app_backend.laundry.presentation.payload.response.LaundryCommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/laundry")
@RequiredArgsConstructor
public class LaundryController implements LaundrySwagger {

    private static final Integer DEFAULT_DISTANCE = 3000;

    private final LaundryService laundryService;

    @GetMapping
    public Mono<ApiCommonResponse<List<LaundryCommonResponse>>> getLaundryList(
        @RequestParam Double latitude, @RequestParam Double longitude) {
        return laundryService.findByLocationAndDistance(latitude,
                longitude, DEFAULT_DISTANCE).collectList()
            .map(ApiCommonResponse::createSuccessResponse);
    }
}
