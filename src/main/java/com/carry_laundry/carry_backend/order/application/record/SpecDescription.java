package com.carry_laundry.carry_backend.order.application.record;

import com.carry_laundry.carry_backend.order.domain.entity.OrderSpecification;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundrySpecType;
import jakarta.validation.constraints.NotNull;

public record SpecDescription(
    @NotNull LaundrySpecType laundrySpec,
    @NotNull Long value
) {

    public static SpecDescription of(OrderSpecification orderSpecification) {
        return new SpecDescription(LaundrySpecType.valueOf(orderSpecification.getSpecType()),
            orderSpecification.getValue());
    }
}