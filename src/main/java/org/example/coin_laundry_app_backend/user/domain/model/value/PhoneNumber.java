package org.example.coin_laundry_app_backend.user.domain.model.value;

import java.util.regex.Pattern;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.RegionCode;

@Getter
public class PhoneNumber {

    private static final Pattern PATTERN = Pattern.compile("^\\+82\\s\\d{2}-\\d{4}-\\d{4}$");

    private final RegionCode regionCode;
    private final String value;

    private PhoneNumber(String value) {
        validatePhoneNumber(value);
        String[] values = value.split(" ");
        this.regionCode = RegionCode.from(values[0]);
        this.value = values[1];
    }

    public static PhoneNumber from(String value) {
        return new PhoneNumber(value);
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (!PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("잘못된 전화번호 형식입니다.");
        }
    }

    @Override
    public String toString() {
        return regionCode.getValue() + " " + value;
    }
}
