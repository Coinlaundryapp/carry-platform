package com.carry_laundry.carry_backend.order.application.record;

import jakarta.validation.constraints.NotNull;

public record OrderSchedule(
        @NotNull String desiredPickupDateTime,
        @NotNull String desiredDeliveryDateTime
) {

    public static OrderSchedule create(String desiredPickupDateTime, String desiredDeliveryDateTime) {
        return new OrderSchedule(desiredPickupDateTime, desiredDeliveryDateTime);
    }
}
