package org.example.coin_laundry_app_backend.user.domain.model.value;

import java.util.regex.Pattern;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.RegionCode;

@Getter
public class PhoneNumber {

    private static final Pattern PATTERN = Pattern.compile("^010-(\\d{4})-(\\d{4})$");

    private final RegionCode regionCode;
    private final String value;

    private PhoneNumber(String value) {
        validatePhoneNumber(value);
        this.regionCode = RegionCode.SOUTH_KOREA;
        this.value = value;
    }

    public static PhoneNumber from(String value) {
        return new PhoneNumber(value);
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (!PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("Wrong Format Phone Number");
        }
    }

    @Override
    public String toString() {
        return regionCode.getValue() + value;
    }
}
