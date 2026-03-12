ALTER TABLE user_shipping_addresses
    ADD COLUMN recipient_name VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN recipient_phone VARCHAR(20) NOT NULL DEFAULT '',
    ADD COLUMN entrance_info VARCHAR(200),
    ADD COLUMN area_code VARCHAR(20) NOT NULL DEFAULT '';
