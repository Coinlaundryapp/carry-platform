package org.example.coin_laundry_app_backend.user.presentation;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.service.ShippingAddressService;
import org.example.coin_laundry_app_backend.user.domain.model.entity.domainmodel.ShippingAddress;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shipping.UpdateAddressRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class ShippingAddressController {

    private final ShippingAddressService shippingAddressService;

    @GetMapping("/shipping-addresses")
    public Mono<ApiCommonResponse<?>> getAllShippingAddresses(@AuthenticationPrincipal Long userId) {
        return shippingAddressService.getAllShippingAddresses(userId)
                .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping("/shipping-addresses/{shippingAddressId}")
    public Mono<ApiCommonResponse<?>> getShippingAddressById(@AuthenticationPrincipal Long userId, @PathVariable Long shippingAddressId) {
        return shippingAddressService.getShippingAddressById(userId, shippingAddressId)
                .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/shipping-addresses")
    public Mono<ShippingAddress> addShippingAddress(@AuthenticationPrincipal Long userId, @RequestBody CreateAddressRequest request) {
        return shippingAddressService.addShippingAddress(userId, request);
    }

    @PutMapping("/shipping-addresses/{shippingAddressId}")
    public Mono<ShippingAddress> updateShippingAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long shippingAddressId,
            @RequestBody UpdateAddressRequest request) {
        return shippingAddressService.updateShippingAddress(userId, shippingAddressId, request);
    }

    @DeleteMapping("/shipping-addresses/{shippingAddressId}")
    public Mono<Void> deleteShippingAddress(@AuthenticationPrincipal Long userId, @PathVariable Long shippingAddressId) {
        return shippingAddressService.deleteShippingAddress(userId, shippingAddressId);
    }

    @PatchMapping("/shipping-addresses/{shippingAddressId}/default")
    public Mono<ApiCommonResponse<?>> setDefaultShippingAddress(@AuthenticationPrincipal Long userId, @PathVariable Long shippingAddressId) {
        return shippingAddressService.setDefaultShippingAddress(userId, shippingAddressId).then(Mono.empty().map(ApiCommonResponse::createSuccessResponse));
    }
}
