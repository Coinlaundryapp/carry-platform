package org.example.coin_laundry_app_backend.order.application.service;

import lombok.RequiredArgsConstructor;
import org.example.coin_laundry_app_backend.laundromat.application.LaundromatService;
import org.example.coin_laundry_app_backend.laundromat.domain.entity.Laundromat;
import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.domain.entity.*;
import org.example.coin_laundry_app_backend.order.domain.enums.invoice.ChargeType;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.CreateOrderRequest;
import org.example.coin_laundry_app_backend.user.application.service.ShippingAddressService;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderRequestService {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final TransactionalOperator transactionalOperator;
    // Peer Service
    private final LaundromatService laundromatService;
    private final ShippingAddressService shippingAddressService;
    // Internal Service
    private final PriceInquiryService priceInquiryService;

    @Transactional
    public Mono<Tuple2<Order, Invoice>> createOrder(CreateOrderRequest request, Long userId) {
        int laundryPrice = priceInquiryService.getLaundryPrice(request);
        int serviceFee = (int) (laundryPrice * 0.1);
        return Mono.zip(laundromatService.getLaundromatById(request.getLaundromatId()),
                        shippingAddressService.getShippingAddressById(userId, request.getAddressId()))
                .flatMap(tuple2 -> {
                    Laundromat laundromat = tuple2.getT1();
                    ShippingAddress shippingAddress = tuple2.getT2();
                    Order order = Order.create(request, userId, laundromat.getName());
                    return r2dbcEntityTemplate.insert(Order.class).using(order)
                            .thenReturn(Tuples.of(order, laundromat, shippingAddress));
                })
                .flatMap(tuple3 -> {
                    Order order = tuple3.getT1();
                    ShippingAddress shippingAddress = tuple3.getT3();
                    OrderShippingAddress orderShippingAddress = OrderShippingAddress.create(order, shippingAddress);
                    return r2dbcEntityTemplate.insert(OrderShippingAddress.class).using(orderShippingAddress)
                            .thenReturn(tuple3);
                })
                .flatMap((Tuple3<Order, Laundromat, ShippingAddress> tuple3) -> {
                    Order order = tuple3.getT1();
                    Flux<OrderSpecification> specFlux = Flux.fromIterable(convertSpec(order.getId(), request.getOrderContent()));
                    return specFlux.flatMap(orderSpec -> r2dbcEntityTemplate.insert(OrderSpecification.class).using(orderSpec))
                            .then().thenReturn(tuple3);
                })
                .flatMap((Tuple3<Order, Laundromat, ShippingAddress> tuple3) -> {
                    Order order = tuple3.getT1();
                    Flux<OrderOption> optionFlux = Flux.fromIterable(convertOption(order.getId(), request.getOrderContent()));
                    return optionFlux.flatMap(orderOption -> r2dbcEntityTemplate.insert(OrderOption.class).using(orderOption))
                            .then().thenReturn(tuple3);
                })
                .flatMap((Tuple3<Order, Laundromat, ShippingAddress> tuple3) -> {
                    Order order = tuple3.getT1();
                    Laundromat laundromat = tuple3.getT2();
                    ShippingAddress shippingAddress = tuple3.getT3();
                    int deliveryFee = calculateDeliveryFee(shippingAddress, laundromat);
                    Invoice invoice = Invoice.create(order.getId(), laundryPrice + serviceFee + deliveryFee, 0);
                    return r2dbcEntityTemplate.insert(Invoice.class).using(invoice)
                            .thenReturn(Tuples.of(order, invoice, deliveryFee));
                })
                .flatMap(tuple3 -> {
                    Order order = tuple3.getT1();
                    Invoice invoice = tuple3.getT2();
                    int deliveryFee = tuple3.getT3();
                    Flux<InvoiceCharge> invoiceChargeFlux = Flux.fromIterable(convertInvoiceCharge(invoice.getId(), laundryPrice, deliveryFee, serviceFee));
                    return invoiceChargeFlux.flatMap(invoiceCharge -> r2dbcEntityTemplate.insert(InvoiceCharge.class).using(invoiceCharge))
                            .then().thenReturn(Tuples.of(order, invoice));
                })
                .as(transactionalOperator::transactional);
    }

    private List<OrderOption> convertOption(Long orderId, OrderContent content) {
        return priceInquiryService.getOrderOptions(orderId, content);
    }

    private List<OrderSpecification> convertSpec(Long orderId, OrderContent content) {
        return content.laundrySpecs().stream().map(specDescription -> OrderSpecification.create(orderId, specDescription)).toList();
    }

    private List<InvoiceCharge> convertInvoiceCharge(Long invoiceId, int laundryPrice, int deliveryFee, int serviceFee) {
        List<InvoiceCharge> invoiceCharges = new ArrayList<>();
        invoiceCharges.add(InvoiceCharge.create(invoiceId, ChargeType.LAUNDRY_PRICE, laundryPrice));
        invoiceCharges.add(InvoiceCharge.create(invoiceId, ChargeType.DELIVERY_FEE, deliveryFee));
        invoiceCharges.add(InvoiceCharge.create(invoiceId, ChargeType.SERVICE_FEE, serviceFee));
        return invoiceCharges;
    }

    private int calculateDeliveryFee(ShippingAddress shippingAddress, Laundromat laundromat) {
        return 4000;
    }
}
