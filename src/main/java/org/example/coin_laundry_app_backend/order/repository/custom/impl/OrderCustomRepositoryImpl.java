package org.example.coin_laundry_app_backend.order.repository.custom.impl;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.example.coin_laundry_app_backend.order.domain.entity.*;
import org.example.coin_laundry_app_backend.order.repository.custom.OrderCustomRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.List;

public class OrderCustomRepositoryImpl implements OrderCustomRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public OrderCustomRepositoryImpl(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    @Override
    public Mono<Order> getOrderDetail(Long orderId, Long userId) {
        String selectSQL = """
                SELECT to_jsonb(ord) as base,
                       CASE
                            WHEN COUNT(ors) > 0 THEN jsonb_agg(DISTINCT to_jsonb(ors))
                            ELSE '[]'::jsonb
                       END as specs,
                       jsonb_agg(DISTINCT to_jsonb(odo)) as options,
                       to_jsonb(osa) as address,
                       to_jsonb(inv) as invoice,
                       (
                            SELECT jsonb_agg(to_jsonb(invc))
                              FROM invoice_charges invc
                             WHERE inv.id = invc.invoice_id
                       ) AS charges
                FROM orders ord
                LEFT JOIN order_specs ors
                       ON ord.id = ors.order_id
                LEFT JOIN order_options odo
                       ON ord.id = odo.order_id
                LEFT JOIN order_shipping_addresses osa
                       ON ord.id = osa.order_id
                LEFT JOIN invoices inv
                       ON ord.id = inv.order_id
                WHERE ord.id = :orderId AND ord.customer_id = :userId
                GROUP BY ord.id, osa.id, inv.id
                """;

        DatabaseClient.GenericExecuteSpec spec = r2dbcEntityTemplate.getDatabaseClient().sql(selectSQL)
                .bind("orderId", orderId)
                .bind("userId", userId);

        return spec.map((row, rowMetadata) -> {
            String baseJson = row.get("base", String.class);
            String specsJson = row.get("specs", String.class);
            String optionsJson = row.get("options", String.class);
            String addressJson = row.get("address", String.class);
            String invoiceJson = row.get("invoice", String.class);
            String chargesJson = row.get("charges", String.class);
            return mapToObject(
                    baseJson, specsJson, optionsJson,
                    addressJson, invoiceJson, chargesJson);
        }).one();
    }

    private Order mapToObject(String baseJson, String specsJson, String optionsJson,
                                  String addressJson, String invoiceJson, String chargesJson) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            Order order = objectMapper.readValue(baseJson, new TypeReference<>() {});
            if (specsJson != null) {
                List<OrderSpecification> specs = objectMapper.readValue(specsJson, new TypeReference<>() {});
                order.setOrderSpecifications(specs);
            }
            if (optionsJson != null) {
                List<OrderOption> options = objectMapper.readValue(optionsJson, new TypeReference<>() {});
                order.setOrderOptions(options);
            }
            if (addressJson != null) {
                OrderShippingAddress address = objectMapper.readValue(addressJson, new TypeReference<>() {});
                order.setOrderShippingAddress(address);
            }
            if (invoiceJson != null) {
                Invoice invoice = objectMapper.readValue(invoiceJson, new TypeReference<>() {});
                if (chargesJson != null) {
                    List<InvoiceCharge> charges = objectMapper.readValue(chargesJson, new TypeReference<>() {});
                    invoice.setInvoiceCharges(charges);
                }
                order.setInvoice(invoice);
            }
            return order;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}