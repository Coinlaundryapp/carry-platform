package com.carry_laundry.carry_backend.payment.repository;

import com.carry_laundry.carry_backend.payment.domain.entity.Payment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
}
