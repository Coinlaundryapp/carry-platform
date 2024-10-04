package com.carry_laundry.carry_backend.order.repository;

import com.carry_laundry.carry_backend.order.domain.entity.Invoice;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InvoiceRepository extends ReactiveCrudRepository<Invoice, Long> {

}
