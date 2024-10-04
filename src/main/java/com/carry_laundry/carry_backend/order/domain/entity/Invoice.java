package com.carry_laundry.carry_backend.order.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Setter
@Getter
@Table("invoices")
@AllArgsConstructor
@NoArgsConstructor
public class Invoice {

    @Id
    private Long id;
    private Long orderId;
    private Integer totalAmount;
    private Integer discountAmount;
    private Integer netAmount;
    @CreatedDate
    private String createdAt;
    @LastModifiedDate
    private String updatedAt;
    @Transient
    private List<InvoiceCharge> invoiceCharges;

    public static Invoice create(Long orderId, Integer totalAmount, Integer discountAmount) {
        return Invoice.builder()
            .orderId(orderId)
            .totalAmount(totalAmount)
            .discountAmount(discountAmount)
            .netAmount(totalAmount - discountAmount)
            .build();
    }
}
