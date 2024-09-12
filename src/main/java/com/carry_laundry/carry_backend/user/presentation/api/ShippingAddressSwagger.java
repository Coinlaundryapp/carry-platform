package com.carry_laundry.carry_backend.user.presentation.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Shipping Address", description = "배송지 관리 API")
@SecurityRequirement(name = "jwtAuth")
public interface ShippingAddressSwagger {

}
