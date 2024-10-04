package com.carry_laundry.carry_backend.payment.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
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
