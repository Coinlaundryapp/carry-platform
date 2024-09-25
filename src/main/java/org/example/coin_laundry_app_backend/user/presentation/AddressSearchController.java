package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.AddressSearchService;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.address.SearchAddressRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressSearchController {

    final private AddressSearchService addressSearchService;

    @GetMapping
    public Mono<ApiCommonResponse<?>> searchAddresses(SearchAddressRequest request) {
        return addressSearchService.fetchAndTransformData(request.query(), request.pageNumber(), request.pageSize())
                .map(ApiCommonResponse::createSuccessResponse);
    }
}