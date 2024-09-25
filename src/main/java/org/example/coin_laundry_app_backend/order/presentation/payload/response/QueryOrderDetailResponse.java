package org.example.coin_laundry_app_backend.order.presentation.payload.response;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.application.record.OrderSchedule;
import org.example.coin_laundry_app_backend.order.application.record.payment.PaymentDetail;
import org.example.coin_laundry_app_backend.order.domain.enums.orderdetail.OrderDetailStatus;
import org.example.coin_laundry_app_backend.user.domain.entity.ShippingAddress;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class QueryOrderDetailResponse {
    Long id;
    OrderDetailStatus status;
    OrderContent orderContent;
    String laundromatName;
    ShippingAddress shippingAddress;
    OrderSchedule orderSchedule;
    Map<String, PaymentDetail> paymentDetails;

    public static QueryOrderDetailResponse of(Long id, OrderDetailStatus status,
                                              OrderContent orderContent, String laundromatName, ShippingAddress shippingAddress,
                                              OrderSchedule orderSchedule, PaymentDetail paymentDetail
    ) {
        Map<String, PaymentDetail> paymentDetails = new HashMap<>();
        if (status.isPriceConfirmed()) {
            paymentDetails.put("confirmedPayment", paymentDetail);
            paymentDetails.put("estimatedPayment", null);
        } else {
            paymentDetails.put("confirmedPayment", null);
            paymentDetails.put("estimatedPayment", paymentDetail);
        }
        return QueryOrderDetailResponse.builder()
                .id(id)
                .status(status)
                .orderContent(orderContent)
                .laundromatName(laundromatName)
                .orderSchedule(orderSchedule)
                .shippingAddress(shippingAddress)
                .paymentDetails(paymentDetails)
                .build();
    }
}
