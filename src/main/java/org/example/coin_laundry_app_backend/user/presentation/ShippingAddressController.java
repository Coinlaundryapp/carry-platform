package org.example.coin_laundry_app_backend.user.presentation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.common.presentation.payload.ApiCommonResponse;
import org.example.coin_laundry_app_backend.user.application.record.shippingaddress.ShippingAddressSummary;
import org.example.coin_laundry_app_backend.user.application.service.ShippingAddressService;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;
import org.example.coin_laundry_app_backend.user.presentation.api.ShippingAddressSwagger;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.CreateAddressRequest;
import org.example.coin_laundry_app_backend.user.presentation.payload.request.shippingaddress.UpdateAddressRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class ShippingAddressController implements ShippingAddressSwagger {

    private final ShippingAddressService shippingAddressService;

    @GetMapping
    public Mono<ApiCommonResponse<ShippingAddress>> getDefaultShippingAddress(
        @AuthenticationPrincipal Long userId) {
        return shippingAddressService.getDefaultShippingAddress(userId)
            .map(ApiCommonResponse::createSuccessResponse)
            .defaultIfEmpty(
                ApiCommonResponse.createApiResponse(HttpStatus.NO_CONTENT, "No Content", null));
    }

    @GetMapping("/shipping-addresses")
    public Mono<ApiCommonResponse<List<ShippingAddressSummary>>> getAllShippingAddresses(
        @AuthenticationPrincipal Long userId) {
        return shippingAddressService.getAllShippingAddresses(userId)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @GetMapping("/shipping-addresses/{shippingAddressId}")
    public Mono<ApiCommonResponse<ShippingAddress>> getShippingAddressById(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long shippingAddressId) {
        return shippingAddressService.getShippingAddressById(userId, shippingAddressId)
            .map(ApiCommonResponse::createSuccessResponse);
    }

    @PostMapping("/shipping-addresses")
    public Mono<ShippingAddress> addShippingAddress(@AuthenticationPrincipal Long userId,
        @RequestBody CreateAddressRequest request) {
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
    public Mono<Void> deleteShippingAddress(@AuthenticationPrincipal Long userId,
        @PathVariable Long shippingAddressId) {
        return shippingAddressService.deleteShippingAddress(userId, shippingAddressId);
    }

    @PatchMapping("/shipping-addresses/{shippingAddressId}/default")
    public Mono<ApiCommonResponse<Void>> setDefaultShippingAddress(
        @AuthenticationPrincipal Long userId, @PathVariable Long shippingAddressId) {
        return shippingAddressService.setDefaultShippingAddress(userId, shippingAddressId)
            .then(Mono.just(ApiCommonResponse.createSuccessResponse()));
    }
}
