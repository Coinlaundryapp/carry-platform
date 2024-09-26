package org.example.coin_laundry_app_backend.payment.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("payments")
@AllArgsConstructor
public class Payment {

    @Id
    private Long id;
    private Long orderId;
    private String paymentKey;
    private Integer amount;
    private Boolean approved;
}
