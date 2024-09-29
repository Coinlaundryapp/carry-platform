package com.carry_laundry.carry_backend.user.presentation;

import com.carry_laundry.carry_backend.common.presentation.payload.ApiCommonResponse;
import com.carry_laundry.carry_backend.user.application.service.AddressSearchService;
import com.carry_laundry.carry_backend.user.presentation.payload.request.address.SearchAddressRequest;
import lombok.RequiredArgsConstructor;
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
        return addressSearchService.fetchAndTransformData(request.query(), request.pageNumber(),
                request.pageSize())
            .map(ApiCommonResponse::createSuccessResponse);
    }
}