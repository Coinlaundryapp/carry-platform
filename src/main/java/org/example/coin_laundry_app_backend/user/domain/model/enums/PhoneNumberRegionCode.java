package org.example.coin_laundry_app_backend.user.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PhoneNumberRegionCode {

    SOUTH_KOREA("+82");

    private final String value;

    public static PhoneNumberRegionCode from(String value) {
        for (PhoneNumberRegionCode regionCode : PhoneNumberRegionCode.values()) {
            if (regionCode.getValue().equals(value)) {
                return regionCode;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 국가 코드입니다.");
    }
}
