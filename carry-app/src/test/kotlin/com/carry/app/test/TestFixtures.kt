package com.carry.app.test

import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

object TestFixtures {

    const val CUSTOMER_ID = 1L
    const val CARRIER_ID = 2L
    const val LAUNDROMAT_ID = 1L
    const val SHIPPING_ADDRESS_ID = 1L
    const val AREA_CODE = "GANGNAM"

    fun insertCustomer(jdbc: JdbcTemplate, id: Long = CUSTOMER_ID) {
        jdbc.update(
            """
            INSERT INTO user_users (id, email, name, phone, role, oauth_provider, oauth_id, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            id, "customer$id@test.com", "테스트고객$id", "010-1234-5678",
            "CUSTOMER", "KAKAO", "kakao_$id", true,
        )
    }

    fun insertCarrier(jdbc: JdbcTemplate, id: Long = CARRIER_ID) {
        jdbc.update(
            """
            INSERT INTO user_users (id, email, name, phone, role, oauth_provider, oauth_id, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            id, "carrier$id@test.com", "테스트캐리어$id", "010-9876-5432",
            "CARRIER", "KAKAO", "kakao_carrier_$id", true,
        )
    }

    fun insertLaundromat(jdbc: JdbcTemplate, id: Long = LAUNDROMAT_ID) {
        jdbc.update(
            """
            INSERT INTO laundromat_laundromats (id, name, road_address, latitude, longitude)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            id, "테스트세탁소", "서울시 강남구 테헤란로 123", 37.5065, 127.0536,
        )
    }

    fun insertCarrierArea(jdbc: JdbcTemplate, carrierId: Long = CARRIER_ID, areaCode: String = AREA_CODE) {
        jdbc.update(
            """
            INSERT INTO dispatch_carrier_areas (carrier_id, area_code, area_name, active)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (carrier_id, area_code) DO NOTHING
            """,
            carrierId, areaCode, "강남구", true,
        )
    }

    fun desiredPickupAt(): Instant = Instant.now().plus(2, ChronoUnit.HOURS)

    fun desiredDeliveryAt(): Instant = Instant.now().plus(24, ChronoUnit.HOURS)

    fun truncateAll(jdbc: JdbcTemplate) {
        jdbc.execute(
            """
            TRUNCATE TABLE outbox_events CASCADE;
            TRUNCATE TABLE processed_events CASCADE;
            DELETE FROM dispatch_penalty_records;
            DELETE FROM dispatch_carrier_areas;
            DELETE FROM dispatch_dispatches;
            DELETE FROM delivery_step_media;
            DELETE FROM delivery_steps;
            DELETE FROM delivery_deliveries;
            DELETE FROM payment_payments;
            DELETE FROM payment_invoice_line_items;
            DELETE FROM payment_invoices;
            DELETE FROM order_selected_options;
            DELETE FROM orders;
            DELETE FROM laundromat_media_resources;
            DELETE FROM laundromat_options;
            DELETE FROM laundromat_laundromats;
            DELETE FROM user_shipping_addresses;
            DELETE FROM user_users;
            """
        )
    }
}
