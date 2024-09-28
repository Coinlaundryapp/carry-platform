package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.coin_laundry_app_backend.order.domain.enums.invoice.ChargeType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Getter
@Table("invoice_charges")
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceCharge {

    @Id
    private Long id;
    private Long invoiceId;
    private String chargeType;
    private Integer amount;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;

    public static InvoiceCharge create(Long invoiceId, ChargeType chargeType, Integer amount) {
        return InvoiceCharge.builder()
                .invoiceId(invoiceId)
                .chargeType(chargeType.name())
                .amount(amount)
                .build();
    }
}
