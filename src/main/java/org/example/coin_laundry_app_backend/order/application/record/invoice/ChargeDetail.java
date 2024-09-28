package org.example.coin_laundry_app_backend.order.application.record.invoice;

import com.sun.jdi.CharType;
import org.example.coin_laundry_app_backend.order.domain.entity.InvoiceCharge;
import org.example.coin_laundry_app_backend.order.domain.enums.invoice.ChargeType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ChargeDetail(
        int laundryPrice,
        int deliveryFee,
        int serviceFee
) {
    public int getTotalAmount() {
        return laundryPrice + deliveryFee + serviceFee;
    }

    public static ChargeDetail of(List<InvoiceCharge> invoiceCharges) {
        Map<String, Integer> chargeMap = invoiceCharges.stream()
                .collect(Collectors.toMap(
                        InvoiceCharge::getChargeType,
                        InvoiceCharge::getAmount
                ));

        int laundryPrice = chargeMap.getOrDefault(ChargeType.LAUNDRY_PRICE.name(), 0);
        int deliveryFee = chargeMap.getOrDefault(ChargeType.DELIVERY_FEE.name(), 0);
        int serviceFee = chargeMap.getOrDefault(ChargeType.SERVICE_FEE.name(), 0);

        return new ChargeDetail(laundryPrice, deliveryFee, serviceFee);
    }
}
