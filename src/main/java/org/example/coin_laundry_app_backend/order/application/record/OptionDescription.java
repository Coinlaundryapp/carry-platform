package org.example.coin_laundry_app_backend.order.application.record;

import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record OptionDescription(
        @NotNull boolean selectable,
        @Nullable Integer price
) {}
