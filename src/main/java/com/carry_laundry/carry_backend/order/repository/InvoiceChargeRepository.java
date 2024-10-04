package com.carry_laundry.carry_backend.order.repository;

import com.carry_laundry.carry_backend.order.domain.entity.InvoiceCharge;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface InvoiceChargeRepository extends ReactiveCrudRepository<InvoiceCharge, Long> {
}
