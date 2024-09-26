package org.example.coin_laundry_app_backend.order.application.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.ProcessIdUtil;
import org.example.coin_laundry_app_backend.geo.application.service.record.GeoCoordinate;
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
import reactor.util.function.Tuple2;

@Service
@RequiredArgsConstructor
public class OrderRequestService {

    private final OrderRepository orderRepository;
    private final OrderOptionRepository orderOptionRepository;
    private final OrderShippingAddressRepository orderShippingAddressRepository;
    private final OrderSpecificationRepository orderSpecificationRepository;

    private final ShippingAddressService shippingAddressService;
    private final LaundromatService laundromatService;

    private final PriceInquiryService priceInquiryService;


    private Integer getFee(GeoCoordinate a, GeoCoordinate b) {
        return 25;
    }

    @Transactional
    public Mono<Void> createOrder(CreateOrderRequest request, Long userId) {
        // 1. request
        System.out.println(request.getAddressId());
        // 2.
        System.out.println(request.getLaundromatId());

        Mono<GeoCoordinate> laundromatCoordinate = laundromatService.getCoordinateById(1L);
        Mono<GeoCoordinate> shippingAddressCoordinate = shippingAddressService.getCoordinateById(1L);
        Mono<Tuple2<GeoCoordinate, GeoCoordinate>> combined = Mono.zip(laundromatCoordinate, shippingAddressCoordinate);
//        combined.map(tuple -> getFee(tuple.getT1(), tuple.getT2())
//        ).map(fee ->{
//            // Get Price
//            Order order = new Order();
//        }).subscribe(() )

        // TODO 예상 세탁료
        // TODO 예상 대행료
        // TODO 예상 배송비
        // orderRepository.save(Order.create()).then(Mono.empty());

        return laundromatService.getCoordinateById(1L).mapNotNull(geoCoordinate -> {
            System.out.println(geoCoordinate);
            return null;
        }).then();
    }
}
