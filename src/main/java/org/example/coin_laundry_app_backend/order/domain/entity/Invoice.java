package org.example.coin_laundry_app_backend.order.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("invoices")
@AllArgsConstructor
public class Invoice {

    @Id
    private Long id;
    private Long orderId;
    private Long totalAmount;
    private Long discountAmount;
    private Long netAmount;
}
