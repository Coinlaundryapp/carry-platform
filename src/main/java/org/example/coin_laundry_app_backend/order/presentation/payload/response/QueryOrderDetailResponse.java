package org.example.coin_laundry_app_backend.order.presentation.payload.response;

import lombok.Builder;
import lombok.Data;
import org.example.coin_laundry_app_backend.order.application.record.OrderContent;
import org.example.coin_laundry_app_backend.order.application.record.OrderSchedule;
import org.example.coin_laundry_app_backend.order.application.record.ShippingAddressView;
import org.example.coin_laundry_app_backend.order.application.record.invoice.InvoiceDetail;
import org.example.coin_laundry_app_backend.order.domain.enums.orderdetail.OrderDetailStatus;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class QueryOrderDetailResponse {
    Long id;
    OrderDetailStatus status;
    OrderContent orderContent;
    String laundromatName;
    ShippingAddressView shippingAddressView;
    OrderSchedule orderSchedule;
    Map<String, InvoiceDetail> paymentDetails;

    public static QueryOrderDetailResponse of(Long id, OrderDetailStatus status,
                                              OrderContent orderContent, String laundromatName, ShippingAddressView shippingAddressView,
                                              OrderSchedule orderSchedule, InvoiceDetail invoiceDetail
    ) {
        Map<String, InvoiceDetail> paymentDetails = new HashMap<>();
        if (status.isPriceConfirmed()) {
            paymentDetails.put("confirmedPayment", invoiceDetail);
            paymentDetails.put("estimatedPayment", null);
        } else {
            paymentDetails.put("confirmedPayment", null);
            paymentDetails.put("estimatedPayment", invoiceDetail);
        }
        return QueryOrderDetailResponse.builder()
                .id(id)
                .status(status)
                .laundromatName(laundromatName)
                .orderContent(orderContent)
                .orderSchedule(orderSchedule)
                .shippingAddressView(shippingAddressView)
                .paymentDetails(paymentDetails)
                .build();
    }
}
