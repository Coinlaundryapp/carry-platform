package com.carry_laundry.carry_backend.order.domain.entity;

import com.carry_laundry.carry_backend.order.domain.enums.invoice.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
