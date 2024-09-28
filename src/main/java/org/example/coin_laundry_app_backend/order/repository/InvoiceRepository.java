package org.example.coin_laundry_app_backend.order.repository;

import org.example.coin_laundry_app_backend.order.domain.entity.Invoice;
import org.example.coin_laundry_app_backend.order.repository.custom.OrderCustomRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InvoiceRepository extends ReactiveCrudRepository<Invoice, Long> {
}
