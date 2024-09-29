package com.carry_laundry.carry_backend.order.application.service;

import com.carry_laundry.carry_backend.order.application.record.OrderContent;
import com.carry_laundry.carry_backend.order.domain.entity.OrderOption;
import com.carry_laundry.carry_backend.order.domain.enums.laundry.LaundryOptionType;
import com.carry_laundry.carry_backend.order.domain.enums.option.AdditionalOption;
import com.carry_laundry.carry_backend.order.domain.enums.option.DryOption;
import com.carry_laundry.carry_backend.order.domain.enums.option.WashOption;
import com.carry_laundry.carry_backend.order.domain.model.OptionCondition;
import com.carry_laundry.carry_backend.order.domain.model.OptionPricePolicy;
import com.carry_laundry.carry_backend.order.domain.model.optionprice.OptionPriceInfo;
import com.carry_laundry.carry_backend.order.domain.model.registry.OptionPriceRegistry;
import com.carry_laundry.carry_backend.order.presentation.payload.request.CreateOrderRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PriceInquiryService {

    private final OptionPriceRegistry priceRegistry;

    // 1.
    public Mono<OptionPricePolicy> getPricePolicy(OptionCondition condition) {
        OptionPricePolicy pricePolicy = priceRegistry.getOptionPricePolicy(condition);
        return Mono.just(pricePolicy);
    }

    // 2
    public List<OrderOption> getOrderOptions(Long orderId, OrderContent content) {
        OptionPricePolicy pricePolicy = priceRegistry.getOptionPricePolicy(
            OptionCondition.of(content));
        List<OrderOption> orderOptions = new ArrayList<>();

        Optional<OrderOption> washOptional = getWashOrderOption(pricePolicy, content.washOption(),
            orderId);
        Optional<OrderOption> dryOptional = getDryOrderOption(pricePolicy, content.dryOption(),
            orderId);
        List<OrderOption> additionalOptions = getAllAdditionalOrderOptions(pricePolicy, content,
            orderId);

        washOptional.ifPresent(orderOptions::add);
        dryOptional.ifPresent(orderOptions::add);
        orderOptions.addAll(additionalOptions);

        return orderOptions;
    }

    // 2.1.
    private Optional<OrderOption> getWashOrderOption(OptionPricePolicy pricePolicy,
        WashOption option, Long orderId) {
        if (option == null) {
            return Optional.empty();
        }
        int price = findWashPrice(pricePolicy, option);
        return Optional.of(
            OrderOption.create(orderId, LaundryOptionType.WASH, Objects.requireNonNull(option.of()),
                price));
    }

    // 2.2.
    private Optional<OrderOption> getDryOrderOption(OptionPricePolicy pricePolicy, DryOption option,
        Long orderId) {
        if (option == null) {
            return Optional.empty();
        }
        int price = findDryPrice(pricePolicy, option);
        return Optional.of(
            OrderOption.create(orderId, LaundryOptionType.DRY, Objects.requireNonNull(option.of()),
                price));
    }

    // 2.4.
    private OrderOption getAdditionalOrderOption(OptionPricePolicy pricePolicy,
        AdditionalOption option, Long orderId) {
        int price = findAdditionalPrice(pricePolicy, option);
        return OrderOption.create(orderId, LaundryOptionType.ADDITIONAL,
            Objects.requireNonNull(option.of()), price);
    }

    // 2.3.
    private List<OrderOption> getAllAdditionalOrderOptions(OptionPricePolicy pricePolicy,
        OrderContent content, Long orderId) {
        List<OrderOption> orderOptions = new ArrayList<>();
        List<AdditionalOption> additionalOptions = content.additionalOptions();
        if (additionalOptions.isEmpty()) {
            return orderOptions;
        }
        return additionalOptions.stream().map(
                additionalOption -> getAdditionalOrderOption(pricePolicy, additionalOption, orderId))
            .toList();
    }

    // 3.
    public int getLaundryPrice(CreateOrderRequest request) {
        OrderContent content = request.getOrderContent();
        OptionPricePolicy pricePolicy = priceRegistry.getOptionPricePolicy(
            OptionCondition.of(content));
        int total = 0;
        total += getWashPrice(pricePolicy, content);
        total += getDryPrice(pricePolicy, content);
        total += getAllAdditionalPrice(pricePolicy, content);
        return total;
    }

    private int getWashPrice(OptionPricePolicy pricePolicy, OrderContent content) {
        WashOption washOption = content.washOption();
        if (washOption == null) {
            return 0;
        }
        return findWashPrice(pricePolicy, washOption);
    }

    private int getDryPrice(OptionPricePolicy pricePolicy, OrderContent content) {
        DryOption dryOption = content.dryOption();
        if (dryOption == null) {
            return 0;
        }
        return findDryPrice(pricePolicy, dryOption);
    }

    private int getAdditionalPrice(OptionPricePolicy pricePolicy,
        AdditionalOption additionalOption) {
        return findAdditionalPrice(pricePolicy, additionalOption);
    }

    private int getAllAdditionalPrice(OptionPricePolicy pricePolicy, OrderContent content) {
        List<AdditionalOption> additionalOptions = content.additionalOptions();
        if (additionalOptions.isEmpty()) {
            return 0;
        }
        return additionalOptions.stream()
            .map(additionalOption -> getAdditionalPrice(pricePolicy, additionalOption))
            .reduce(0, Integer::sum);
    }

    // 4. Common
    private int findWashPrice(OptionPricePolicy pricePolicy, WashOption washOption) {
        OptionPriceInfo priceInfo = pricePolicy.getWashOption();
        return priceInfo.getPrice(washOption.of());
    }

    private int findDryPrice(OptionPricePolicy pricePolicy, DryOption dryOption) {
        OptionPriceInfo priceInfo = pricePolicy.getDryOption();
        return priceInfo.getPrice(dryOption.of());
    }

    private int findAdditionalPrice(OptionPricePolicy pricePolicy,
        AdditionalOption additionalOption) {
        OptionPriceInfo priceInfo = pricePolicy.getAdditionalOption();
        return priceInfo.getPrice(additionalOption.of());
    }
}
