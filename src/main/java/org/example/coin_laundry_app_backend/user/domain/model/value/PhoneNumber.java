package org.example.coin_laundry_app_backend.user.domain.model.value;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.util.regex.Pattern;
import lombok.Getter;
import org.example.coin_laundry_app_backend.user.domain.model.enums.PhoneNumberRegionCode;
import org.springframework.web.client.HttpClientErrorException;

@Getter
public class PhoneNumber {

    private static final Pattern PATTERN = Pattern.compile("^\\+82\\s\\d{2}-\\d{4}-\\d{4}$");

    private final PhoneNumberRegionCode regionCode;
    private final String value;

    private PhoneNumber(String value) {
        validatePhoneNumber(value);
        String[] values = value.split(" ");
        this.regionCode = PhoneNumberRegionCode.from(values[0]);
        this.value = values[1];
    }

    public static PhoneNumber from(String value) {
        return new PhoneNumber(value);
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new HttpClientErrorException(UNPROCESSABLE_ENTITY, "전화번호가 없으면 서비스를 이용할 수 없습니다.");
        }
        if (!PATTERN.matcher(phoneNumber).matches()) {
            throw new IllegalArgumentException("잘못된 전화번호 형식입니다.");
        }
    }

    @Override
    public String toString() {
        return regionCode.getValue() + " " + value;
    }
}
