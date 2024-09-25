package org.example.coin_laundry_app_backend.order.application.record;

import jakarta.validation.constraints.NotNull;

public record OrderSchedule(
        @NotNull String desiredPickupDateTime,
        @NotNull String desiredDeliveryDate
) {}
