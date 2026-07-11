package com.carry.app.test

import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    fun insertShippingAddress(jdbc: JdbcTemplate, userId: Long = CUSTOMER_ID, id: Long = SHIPPING_ADDRESS_ID, areaCode: String = AREA_CODE) {
        jdbc.update(
            """
            INSERT INTO user_shipping_addresses (id, user_id, alias, road_address, detail_address, zip_code, latitude, longitude, recipient_name, recipient_phone, area_code, is_default)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            id, userId, "집", "서울시 강남구 테헤란로 123", "4층", "06234",
            37.5065, 127.0536, "테스트고객", "010-1234-5678", areaCode, true,
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

    fun insertServiceArea(jdbc: JdbcTemplate, areaCode: String = AREA_CODE) {
        jdbc.update(
            """
            INSERT INTO service_areas (id, area_code, name, status)
            VALUES (1, ?, ?, 'ACTIVE')
            ON CONFLICT (area_code) DO NOTHING
            """,
            areaCode, "강남구",
        )
        // 월~일 00:00~23:59 운영 (테스트 편의상 전일 운영)
        for (day in 1..7) {
            jdbc.update(
                """
                INSERT INTO service_area_schedules (service_area_id, day_of_week, open_time, close_time)
                VALUES (1, ?, ?, ?)
                ON CONFLICT (service_area_id, day_of_week) DO NOTHING
                """,
                day, LocalTime.of(0, 0), LocalTime.of(23, 59),
            )
        }
    }

    private val KST = ZoneId.of("Asia/Seoul")

    // 희망 시각은 **고정 KST 시각**으로 둔다. now.plus(Nh) 방식은 실행 시점의 time-of-day가 그대로 남아,
    // CI가 23:59 KST에 돌면 전일 운영(00:00~23:59) 종료 경계(23:59:00)를 넘겨 OutsideOperatingHours로
    // 깨졌다(시각 의존 플레이크). 익일 10:00·익익일 14:00 KST는 시계와 무관하게 항상 운영시간 내·미래다.
    fun desiredPickupAt(): Instant =
        LocalDate.now(KST).plusDays(1).atTime(10, 0).atZone(KST).toInstant()

    fun desiredDeliveryAt(): Instant =
        LocalDate.now(KST).plusDays(2).atTime(14, 0).atZone(KST).toInstant()

    fun truncateAll(jdbc: JdbcTemplate) {
        jdbc.execute(
            """
            TRUNCATE TABLE outbox_events CASCADE;
            TRUNCATE TABLE processed_events CASCADE;
            DELETE FROM service_area_holidays;
            DELETE FROM service_area_schedules;
            DELETE FROM service_areas;
            DELETE FROM dispatch_penalty_records;
            DELETE FROM dispatch_carrier_areas;
            DELETE FROM dispatch_dispatches;
            DELETE FROM delivery_step_media;
            DELETE FROM delivery_steps;
            DELETE FROM delivery_deliveries;
            DELETE FROM review_media;
            DELETE FROM review_reviews;
            DELETE FROM payment_ledger_entries;
            DELETE FROM payment_reconciliation_mismatches;
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
