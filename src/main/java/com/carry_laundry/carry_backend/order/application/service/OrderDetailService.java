package com.carry_laundry.carry_backend.order.application.service;

import com.carry_laundry.carry_backend.order.application.record.OrderContent;
import com.carry_laundry.carry_backend.order.application.record.OrderSchedule;
import com.carry_laundry.carry_backend.order.application.record.ShippingAddressView;
import com.carry_laundry.carry_backend.order.application.record.invoice.InvoiceDetail;
import com.carry_laundry.carry_backend.order.domain.enums.orderdetail.OrderDetailStatus;
import com.carry_laundry.carry_backend.order.presentation.payload.response.QueryOrderDetailResponse;
import com.carry_laundry.carry_backend.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderDetailService {

    private final OrderRepository orderRepository;

    public Mono<QueryOrderDetailResponse> getDetail(Long orderId, Long userId) {
        return orderRepository.getOrderDetail(orderId, userId)
            .map(order -> {
                Long id = order.getId();
                OrderDetailStatus status = OrderDetailStatus.valueOf(order.getStatus());
                OrderContent orderContent = OrderContent.of(order);
                String laundromatName = order.getLaundromatName();
                ShippingAddressView shippingAddressView = ShippingAddressView.of(
                    order.getOrderShippingAddress());
                OrderSchedule orderSchedule = OrderSchedule.create(order.getDesiredPickupDatetime(),
                    order.getDesiredDeliveryDatetime());
                InvoiceDetail invoiceDetail = InvoiceDetail.of(order.getInvoice());
                return QueryOrderDetailResponse.of(id, status, orderContent, laundromatName,
                    shippingAddressView, orderSchedule, invoiceDetail);
            });
    }
}
