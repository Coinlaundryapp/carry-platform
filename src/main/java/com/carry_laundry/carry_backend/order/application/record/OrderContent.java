package com.carry_laundry.carry_backend.order.application.record;

import com.carry_laundry.carry_backend.order.domain.entity.Order;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryItemType;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryOptionType;
import com.carry_laundry.carry_backend.order.domain.enums.option.AdditionalOption;
import com.carry_laundry.carry_backend.order.domain.enums.option.DryOption;
import com.carry_laundry.carry_backend.order.domain.enums.option.WashOption;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderRequestType;
import com.carry_laundry.carry_backend.order.domain.enums.order.OrderUnitType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record OrderContent(
    @NotNull OrderUnitType orderUnitType,
    @NotNull OrderRequestType orderRequestType,
    @NotNull LaundryItemType laundryItemType,
    @NotNull List<SpecDescription> laundrySpecs,
    @NotNull WashOption washOption,
    @NotNull DryOption dryOption,
    @NotNull List<AdditionalOption> additionalOptions
) {

    public static OrderContent of(Order order) {
        // FIXME 24.09.27 OrderSpecification vs LaundrySpecType
        List<SpecDescription> laundrySpecs = order.getOrderSpecifications().stream()
            .map(SpecDescription::of).toList();

        WashOption washOption = order.getOrderOptions().stream().filter(
                orderOption -> LaundryOptionType.WASH.name().equals(orderOption.getOptionType()))
            .map(orderOption -> WashOption.valueOf(orderOption.getSubOptionType())).findAny()
            .orElse(null);

        DryOption dryOption = order.getOrderOptions().stream()
            .filter(orderOption -> LaundryOptionType.DRY.name().equals(orderOption.getOptionType()))
            .map(orderOption -> DryOption.valueOf(orderOption.getSubOptionType())).findAny()
            .orElse(null);

        List<AdditionalOption> additionalOptions = order.getOrderOptions().stream().filter(
                orderOption -> LaundryOptionType.ADDITIONAL.name().equals(orderOption.getOptionType()))
            .map(orderOption -> AdditionalOption.valueOf(orderOption.getSubOptionType())).toList();

        return new OrderContent(
            OrderUnitType.valueOf(order.getOrderUnitType()),
            OrderRequestType.valueOf(order.getOrderRequestType()),
            LaundryItemType.valueOf(order.getLaundryItemType()),
            laundrySpecs,
            washOption,
            dryOption,
            additionalOptions
        );
    }
}