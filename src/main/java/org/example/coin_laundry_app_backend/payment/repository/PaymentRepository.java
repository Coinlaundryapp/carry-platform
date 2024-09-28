package org.example.coin_laundry_app_backend.payment.repository;

import org.example.coin_laundry_app_backend.payment.domain.entity.Payment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long> {
}
