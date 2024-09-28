package org.example.coin_laundry_app_backend.order.application.record;

import jakarta.validation.constraints.NotNull;
import org.example.coin_laundry_app_backend.order.domain.entity.InvoiceCharge;
import org.example.coin_laundry_app_backend.order.domain.entity.Order;
import org.example.coin_laundry_app_backend.order.domain.entity.OrderOption;
import org.example.coin_laundry_app_backend.order.domain.entity.OrderSpecification;
import org.example.coin_laundry_app_backend.order.domain.enums.invoice.ChargeType;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryItemType;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundryOptionType;
import org.example.coin_laundry_app_backend.order.domain.enums.laundry.LaundrySpecType;
import org.example.coin_laundry_app_backend.order.domain.enums.option.AdditionalOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.DryOption;
import org.example.coin_laundry_app_backend.order.domain.enums.option.WashOption;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderRequestType;
import org.example.coin_laundry_app_backend.order.domain.enums.order.OrderUnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        WashOption washOption = order.getOrderOptions().stream().filter(orderOption -> LaundryOptionType.WASH.name().equals(orderOption.getOptionType()))
                .map(orderOption -> WashOption.valueOf(orderOption.getSubOptionType())).findAny().orElse(null);

        DryOption dryOption = order.getOrderOptions().stream().filter(orderOption -> LaundryOptionType.DRY.name().equals(orderOption.getOptionType()))
                .map(orderOption -> DryOption.valueOf(orderOption.getSubOptionType())).findAny().orElse(null);

        List<AdditionalOption> additionalOptions = order.getOrderOptions().stream().filter(orderOption -> LaundryOptionType.ADDITIONAL.name().equals(orderOption.getOptionType()))
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