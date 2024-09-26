package org.example.coin_laundry_app_backend.order.application.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.ProcessIdUtil;
import org.example.coin_laundry_app_backend.laundromat.application.LaundromatService;
import org.example.coin_laundry_app_backend.order.domain.entity.Order;
import org.example.coin_laundry_app_backend.order.domain.service.OrderDomainService;
import org.example.coin_laundry_app_backend.order.presentation.payload.request.CreateOrderRequest;
import org.example.coin_laundry_app_backend.order.repository.OrderOptionRepository;
import org.example.coin_laundry_app_backend.order.repository.OrderRepository;
import org.example.coin_laundry_app_backend.order.repository.OrderShippingAddressRepository;
import org.example.coin_laundry_app_backend.order.repository.OrderSpecificationRepository;
import org.example.coin_laundry_app_backend.user.application.service.ShippingAddressService;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderRequestService {

    private final OrderRepository orderRepository;
    private final OrderOptionRepository orderOptionRepository;
    private final OrderShippingAddressRepository orderShippingAddressRepository;
    private final OrderSpecificationRepository orderSpecificationRepository;

    private final ShippingAddressService shippingAddressService;
    private final LaundromatService laundromatService;


    @Transactional
    public Mono<Void> createOrder(CreateOrderRequest request, Long userId) {
        // 1. request
        System.out.println(request.getAddressId());
        // 2.
        System.out.println(request.getLaundromatId());



        // TODO 예상 세탁료
        // TODO 예상 대행료
        // TODO 예상 배송비


        return orderRepository.save(Order.create()).then(Mono.empty());
    }
}
