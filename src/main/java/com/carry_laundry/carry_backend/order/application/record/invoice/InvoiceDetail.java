package com.carry_laundry.carry_backend.order.application.record.invoice;

import com.carry_laundry.carry_backend.order.domain.entity.Invoice;

public record InvoiceDetail(
        DiscountDetail discounts,
        ChargeDetail charges,
        int netAmount
) {
    public static InvoiceDetail create(DiscountDetail discounts, ChargeDetail charges) {
        int netAmount = charges.getTotalAmount() - discounts.getDiscountAmount();
        return new InvoiceDetail(discounts, charges, netAmount);
    }

    public static InvoiceDetail of(Invoice invoice) {
        DiscountDetail discounts = new DiscountDetail(null, null);
        ChargeDetail charges = ChargeDetail.of(invoice.getInvoiceCharges());
        return create(discounts, charges);
    }
}
