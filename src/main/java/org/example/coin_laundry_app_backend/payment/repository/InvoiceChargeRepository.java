package org.example.coin_laundry_app_backend.payment.repository;

import org.example.coin_laundry_app_backend.order.domain.entity.InvoiceCharge;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceChargeRepository extends ReactiveCrudRepository<InvoiceCharge, Long> {
}
