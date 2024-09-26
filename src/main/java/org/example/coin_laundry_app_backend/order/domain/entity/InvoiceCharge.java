package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("invoice_charges")
@AllArgsConstructor
public class InvoiceCharge {

    @Id
    private Long id;
    private Long invoiceId;
    private String chargeType;
    private Long amount;
}
