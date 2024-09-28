package org.example.coin_laundry_app_backend.order.repository;

import org.example.coin_laundry_app_backend.order.domain.entity.InvoiceCharge;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InvoiceChargeRepository extends ReactiveCrudRepository<InvoiceCharge, Long> {
}
