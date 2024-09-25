package org.example.coin_laundry_app_backend.order.application.record;

import jakarta.validation.constraints.NotNull;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySpec;
import org.example.coin_laundry_app_backend.order.domain.enums.option.AdditionalOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.DryOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.WashOption;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;

import java.util.List;

public record OrderContent(
        @NotNull OrderUnitType orderUnitType,
        @NotNull OrderRequestType orderRequestType,
        @NotNull LaundryItemType laundryItemType,
        @NotNull List<LaundrySpec> laundrySpecs,
        @NotNull WashOption washOption,
        @NotNull DryOption dryOption,
        @NotNull List<AdditionalOption> additionalOptions
) {}
