package org.example.coin_laundry_app_backend.order.application.record;

import jakarta.validation.constraints.NotNull;
import org.example.coin_laundry_app_backend.order.domain.entity.OrderSpecification;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySpecType;

public record SpecDescription(
        @NotNull LaundrySpecType laundrySpec,
        @NotNull Long value
) {
    public static SpecDescription of(OrderSpecification orderSpecification) {
        return new SpecDescription(LaundrySpecType.valueOf(orderSpecification.getSpecType()), orderSpecification.getValue());
    }
}